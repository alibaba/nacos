/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.naming.backups;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.backups.datasource.DiskFailoverDataSource;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ThreadUtils;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Failover reactor.
 *
 * @author nkorange
 */
public class FailoverReactor implements Closeable {

    private static final String FAILOVER_DIR = "/failover";


    private Map<String, ServiceInfo> serviceMap = new ConcurrentHashMap<>();

    private boolean failoverSwitchEnable;

    private static final long DAY_PERIOD_MINUTES = 24 * 60;

    private final String failoverDir;

    private final ServiceInfoHolder serviceInfoHolder;

    private final ScheduledExecutorService executorService;

    private final FailoverDataSource failoverDataSource;

    private final MultiGauge failoverInstanceCounts;

    private String notifierEventScope;

    public FailoverReactor(ServiceInfoHolder serviceInfoHolder, String cacheDir, String notifierEventScope) {
        this.serviceInfoHolder = serviceInfoHolder;
        this.failoverDir = cacheDir + FAILOVER_DIR;
        this.notifierEventScope = notifierEventScope;
        failoverDataSource = new DiskFailoverDataSource(serviceInfoHolder, cacheDir);
        failoverInstanceCounts = MultiGauge.builder("nacos_naming_client_failover_instances").register(io.micrometer.core.instrument.Metrics.globalRegistry);
        // init executorService
        this.executorService = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("com.alibaba.nacos.naming.failover");
            return thread;
        });
        this.init();
    }

    /**
     * Init.
     */
    public void init() {

        executorService.scheduleWithFixedDelay(new FailoverSwitchRefresher(), 0L, 5000L, TimeUnit.MILLISECONDS);

        executorService.scheduleWithFixedDelay(new FailoverDataWriter(), 30, DAY_PERIOD_MINUTES, TimeUnit.MINUTES);

        // backup file on startup if failover directory is empty.
        executorService.schedule(() -> {
            try {
                File cacheDir = new File(failoverDir);

                if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                    throw new IllegalStateException("failed to create cache dir: " + failoverDir);
                }

                File[] files = cacheDir.listFiles();
                if (files == null || files.length <= 0) {
                    new FailoverDataWriter().run();
                }
            } catch (Throwable e) {
                NAMING_LOGGER.error("[NA] failed to backup file on startup.", e);
            }

        }, 10000L, TimeUnit.MILLISECONDS);
    }


    class FailoverSwitchRefresher implements Runnable {

        @Override
        public void run() {

            FailoverSwitch fSwitch = failoverDataSource.getSwitch();
            if (fSwitch != null && fSwitch.getEnabled() && !failoverSwitchEnable) {
                failoverSwitchEnable = Boolean.TRUE;
                Map<String, ServiceInfo> map = new ConcurrentHashMap<>(200);
                Map<String, FailoverData> failoverData = failoverDataSource.getFailoverData();
                for (Map.Entry<String, FailoverData> entry : failoverData.entrySet()) {
                    map.put(entry.getKey(), (ServiceInfo) entry.getValue().getData());
                }

                if (map.size() > 0) {
                    serviceMap = map;
                }

                return;
            }

            if (fSwitch != null && failoverSwitchEnable && !fSwitch.getEnabled()) {
                failoverSwitchEnable = Boolean.FALSE;
                Map<String, ServiceInfo> map = new ConcurrentHashMap<>(200);
                Map<String, FailoverData> failoverData = failoverDataSource.getFailoverData();
                for (Map.Entry<String, FailoverData> entry : failoverData.entrySet()) {
                    map.put(entry.getKey(), (ServiceInfo) entry.getValue().getData());
                }

                Map<String, ServiceInfo> serviceInfoMap = serviceInfoHolder.getServiceInfoMap();
                for (Map.Entry<String, ServiceInfo> entry : map.entrySet()) {
                    ServiceInfo oldService = entry.getValue();
                    ServiceInfo newService = serviceInfoMap.get(entry.getKey());
                    boolean changed = serviceInfoHolder.isChangedServiceInfo(oldService, newService);
                    if (changed) {
                        NotifyCenter.publishEvent(new InstancesChangeEvent(notifierEventScope, newService.getName(), newService.getGroupName(),
                                newService.getClusters(), newService.getHosts()));
                    }
                }
            }

            serviceMap.clear();
        }
    }

    class FailoverDataWriter extends TimerTask {

        @Override
        public void run() {
            if (isFailoverSwitch()) {
                return;
            }

            Map<String, ServiceInfo> serviceInfoMap = serviceInfoHolder.getServiceInfoMap();
            Map<String, FailoverData> failoverDataMap = new HashMap<>(serviceInfoMap.size());
            for (Map.Entry<String, ServiceInfo> entry : serviceInfoMap.entrySet()) {
                failoverDataMap.put(entry.getKey(), new FailoverData(FailoverData.DataType.naming, entry.getValue()));
            }

            failoverDataSource.saveFailoverData(failoverDataMap);

            Set<String> serviceNames = failoverDataSource.getSwitch().getServiceNames();

            failoverInstanceCounts.register(serviceNames.stream().map(serviceName -> MultiGauge.Row.of(Tags.of("service_name", serviceName), (serviceInfoMap.get(serviceName)).ipCount())).collect(Collectors.toList()), true);
        }
    }

    public boolean isFailoverSwitch() {
        return failoverSwitchEnable;
    }

    public ServiceInfo getService(String key) {
        ServiceInfo serviceInfo = serviceMap.get(key);

        if (serviceInfo == null) {
            serviceInfo = new ServiceInfo();
            serviceInfo.setName(key);
        }

        return serviceInfo;
    }

    /**
     * Add day.
     *
     * @param date start time
     * @param num  add day number
     * @return new date
     */
    public Date addDay(Date date, int num) {
        Calendar startDT = Calendar.getInstance();
        startDT.setTime(date);
        startDT.add(Calendar.DAY_OF_MONTH, num);
        return startDT.getTime();
    }

    /**
     * shutdown ThreadPool.
     * @throws NacosException
     */
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(executorService, NAMING_LOGGER);
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
}
