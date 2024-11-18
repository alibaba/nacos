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
import com.alibaba.nacos.client.naming.cache.InstancesDiffer;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    
    private final InstancesDiffer instancesDiffer;
    
    private FailoverDataSource failoverDataSource;
    
    private String notifierEventScope;
    
    public FailoverReactor(ServiceInfoHolder serviceInfoHolder, String notifierEventScope) {
        this.serviceInfoHolder = serviceInfoHolder;
        this.notifierEventScope = notifierEventScope;
        this.instancesDiffer = new InstancesDiffer();
        Collection<FailoverDataSource> dataSources = NacosServiceLoader.load(FailoverDataSource.class);
        for (FailoverDataSource dataSource : dataSources) {
            failoverDataSource = dataSource;
            NAMING_LOGGER.info("FailoverDataSource type is {}", dataSource.getClass());
            break;
        }
        // init executorService
        this.executorService = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.naming.failover"));
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
                        InstancesDiff diff = instancesDiffer.doDiff(oldService, newService);
                        if (diff.hasDifferent()) {
                            NAMING_LOGGER.info("[NA] failoverdata isChangedServiceInfo. newService:{}",
                                    JacksonUtils.toJson(newService));
                            NotifyCenter.publishEvent(new InstancesChangeEvent(notifierEventScope, newService.getName(),
                                    newService.getGroupName(), newService.getClusters(), newService.getHosts(), diff));
                        }
                        failoverMap.put(entry.getKey(), (ServiceInfo) entry.getValue().getData());
                    }
                    
                    if (failoverMap.size() > 0) {
                        failoverServiceCntMetrics();
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
                            InstancesDiff diff = instancesDiffer.doDiff(oldService, newService);
                            if (diff.hasDifferent()) {
                                NotifyCenter.publishEvent(
                                        new InstancesChangeEvent(notifierEventScope, newService.getName(),
                                                newService.getGroupName(), newService.getClusters(),
                                                newService.getHosts(), diff));
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
    
    public boolean isFailoverSwitch(String serviceName) {
        return failoverSwitchEnable && serviceMap.containsKey(serviceName) && serviceMap.get(serviceName).ipCount() > 0;
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
    
    private void failoverServiceCntMetrics() {
        try {
            for (Map.Entry<String, ServiceInfo> entry : serviceMap.entrySet()) {
                String serviceName = entry.getKey();
                List<Tag> tags = new ArrayList<>();
                tags.add(new ImmutableTag("service_name", serviceName));
                if (Metrics.globalRegistry.find("nacos_naming_client_failover_instances").tags(tags).gauge() == null) {
                    Gauge.builder("nacos_naming_client_failover_instances", () -> serviceMap.get(serviceName).ipCount())
                            .tags(tags).register(Metrics.globalRegistry);
                }
            }
        } catch (Exception e) {
            NAMING_LOGGER.info("[NA] registerFailoverServiceCnt fail.", e);
        }
    }
    
    private void failoverServiceCntMetricsClear() {
        try {
            for (Map.Entry<String, ServiceInfo> entry : serviceMap.entrySet()) {
                Gauge gauge = Metrics.globalRegistry.find("nacos_naming_client_failover_instances")
                        .tag("service_name", entry.getKey()).gauge();
                if (gauge != null) {
                    Metrics.globalRegistry.remove(gauge);
                }
            }
        } catch (Exception e) {
            NAMING_LOGGER.info("[NA] registerFailoverServiceCnt fail.", e);
        }
    }
}
