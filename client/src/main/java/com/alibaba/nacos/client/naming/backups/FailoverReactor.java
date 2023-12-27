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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    
    private Map<String, Meter> meterMap = new HashMap<>(10);
    
    public FailoverReactor(ServiceInfoHolder serviceInfoHolder, String notifierEventScope) {
        this.serviceInfoHolder = serviceInfoHolder;
        this.notifierEventScope = notifierEventScope;
        Collection<FailoverDataSource> dataSources = NacosServiceLoader.load(FailoverDataSource.class);
        for (FailoverDataSource dataSource : dataSources) {
            failoverDataSource = dataSource;
            NAMING_LOGGER.info("FailoverDataSource type is {}", dataSource.getClass());
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
            try {
                FailoverSwitch fSwitch = failoverDataSource.getSwitch();
                if (fSwitch == null) {
                    failoverSwitchEnable = false;
                    return;
                }
                if (fSwitch.getEnabled() != failoverSwitchEnable) {
                    NAMING_LOGGER.info("failover switch changed, new: {}", fSwitch.getEnabled());
                }
                if (fSwitch.getEnabled()) {
                    Map<String, ServiceInfo> failoverMap = new ConcurrentHashMap<>(200);
                    Map<String, FailoverData> failoverData = failoverDataSource.getFailoverData();
                    for (Map.Entry<String, FailoverData> entry : failoverData.entrySet()) {
                        ServiceInfo newService = (ServiceInfo) entry.getValue().getData();
                        ServiceInfo oldService = serviceMap.get(entry.getKey());
                        if (serviceInfoHolder.isChangedServiceInfo(oldService, newService)) {
                            NAMING_LOGGER.info("[NA] failoverdata isChangedServiceInfo. newService:{}",
                                    JacksonUtils.toJson(newService));
                            NotifyCenter.publishEvent(new InstancesChangeEvent(notifierEventScope, newService.getName(),
                                    newService.getGroupName(), newService.getClusters(), newService.getHosts()));
                        }
                        failoverMap.put(entry.getKey(), (ServiceInfo) entry.getValue().getData());
                    }
                    
                    if (failoverMap.size() > 0) {
                        failoverServiceCntMetrics(failoverMap);
                        serviceMap = failoverMap;
                    }
                    
                    failoverSwitchEnable = true;
                    return;
                }
                
                if (failoverSwitchEnable && !fSwitch.getEnabled()) {
                    Map<String, ServiceInfo> serviceInfoMap = serviceInfoHolder.getServiceInfoMap();
                    for (Map.Entry<String, ServiceInfo> entry : serviceMap.entrySet()) {
                        ServiceInfo oldService = entry.getValue();
                        ServiceInfo newService = serviceInfoMap.get(entry.getKey());
                        if (newService != null) {
                            boolean changed = serviceInfoHolder.isChangedServiceInfo(oldService, newService);
                            if (changed) {
                                NotifyCenter.publishEvent(
                                        new InstancesChangeEvent(notifierEventScope, newService.getName(),
                                                newService.getGroupName(), newService.getClusters(),
                                                newService.getHosts()));
                            }
                        }
                    }
                    
                    serviceMap.clear();
                    failoverSwitchEnable = false;
                    failoverServiceCntMetricsClear();
                }
            } catch (Exception e) {
                NAMING_LOGGER.error("FailoverSwitchRefresher run err", e);
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
    
    private void failoverServiceCntMetrics(Map<String, ServiceInfo> failoverMap) {
        try {
            for (Map.Entry<String, ServiceInfo> entry : failoverMap.entrySet()) {
                String serviceName = entry.getKey();
                Gauge register = Gauge
                        .builder("nacos_naming_client_failover_instances", failoverMap.get(serviceName).ipCount(),
                                Integer::intValue).tag("service_name", serviceName)
                        .description("Nacos failover data service count").register(Metrics.globalRegistry);
                meterMap.put(serviceName, register);
            }
        } catch (Exception e) {
            NAMING_LOGGER.info("[NA] registerFailoverServiceCnt fail.", e);
        }
    }
    
    private void failoverServiceCntMetricsClear() {
        try {
            for (Map.Entry<String, Meter> entry : meterMap.entrySet()) {
                Metrics.globalRegistry.remove(entry.getValue());
            }
            meterMap.clear();
        } catch (Exception e) {
            NAMING_LOGGER.info("[NA] registerFailoverServiceCnt fail.", e);
        }
    }
}