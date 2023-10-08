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
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.ThreadUtils;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
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

    private Map<String, ServiceInfo> serviceMap = new ConcurrentHashMap<>();

    private boolean failoverSwitchEnable;

    private final ServiceInfoHolder serviceInfoHolder;

    private final ScheduledExecutorService executorService;

    private FailoverDataSource failoverDataSource;

    private String notifierEventScope;

    private MultiGauge failoverInstanceCounts = MultiGauge.builder("nacos_naming_client_failover_instances").description("Nacos failover data service count").register(Metrics.globalRegistry);

    public FailoverReactor(ServiceInfoHolder serviceInfoHolder, String cacheDir, String notifierEventScope) {
        this.serviceInfoHolder = serviceInfoHolder;
        this.notifierEventScope = notifierEventScope;
        Collection<FailoverDataSource> dataSources = NacosServiceLoader.load(FailoverDataSource.class);
        for (FailoverDataSource dataSource : dataSources) {
            failoverDataSource = dataSource;
            break;
        }
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

    }

    class FailoverSwitchRefresher implements Runnable {

        @Override
        public void run() {

            FailoverSwitch fSwitch = failoverDataSource.getSwitch();
            if (fSwitch != null && fSwitch.getEnabled()) {
                failoverSwitchEnable = true;
                Map<String, ServiceInfo> failoverMap = new ConcurrentHashMap<>(200);
                Map<String, FailoverData> failoverData = failoverDataSource.getFailoverData();
                for (Map.Entry<String, FailoverData> entry : failoverData.entrySet()) {
                    failoverMap.put(entry.getKey(), (ServiceInfo) entry.getValue().getData());
                }

                if (failoverMap.size() > 0) {
                    failoverInstanceCounts.register(failoverMap.keySet().stream().map(serviceName -> MultiGauge.Row.of(Tags.of("service_name", serviceName), ((ServiceInfo)failoverMap.get(serviceName)).ipCount())).collect(Collectors.toList()), true);
                    serviceMap = failoverMap;
                }

                return;
            }

            if (fSwitch != null && failoverSwitchEnable && !fSwitch.getEnabled()) {
                failoverSwitchEnable = false;
                Map<String, ServiceInfo> serviceInfoMap = serviceInfoHolder.getServiceInfoMap();
                for (Map.Entry<String, ServiceInfo> entry : serviceMap.entrySet()) {
                    ServiceInfo oldService = entry.getValue();
                    ServiceInfo newService = serviceInfoMap.get(entry.getKey());
                    boolean changed = serviceInfoHolder.isChangedServiceInfo(oldService, newService);
                    if (changed) {
                        NotifyCenter.publishEvent(new InstancesChangeEvent(notifierEventScope, newService.getName(), newService.getGroupName(),
                                newService.getClusters(), newService.getHosts()));
                    }
                }
                serviceMap.clear();
                return;
            }
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
     *
     * @throws NacosException Nacos exception
     */
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(executorService, NAMING_LOGGER);
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
}
