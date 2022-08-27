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

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.DeltaResources;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushChange;
import com.alibaba.nacos.istio.util.IstioCrdUtil;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildClusterName;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildServiceEntryName;
import static com.alibaba.nacos.istio.util.IstioExecutor.cycleDebounce;
import static com.alibaba.nacos.istio.util.IstioExecutor.debouncePushChange;

/**
 * NacosServiceInfoResourceWatcher.
 *
 * @author special.fy
 */
@org.springframework.stereotype.Service
public class NacosServiceInfoResourceWatcher extends SmartSubscriber {
    
    private final Map<String, IstioService> serviceInfoMap = new ConcurrentHashMap<>(16);
    
    private final Map<String, Service> serviceCache = new ConcurrentHashMap<>(16);
    
    private final Queue<PushChange> pushChangeQueue = new ConcurrentLinkedQueue<>();
    
    private boolean flagNotify = true;
    
    @Autowired
    private IstioConfig istioConfig;
    
    @Autowired
    private ServiceStorage serviceStorage;
    
    @Autowired
    private EventProcessor eventProcessor;
    
    public NacosServiceInfoResourceWatcher() {
        NotifyCenter.registerSubscriber(this, NamingEventPublisherFactory.getInstance());
    }
    
    public Map<String, IstioService> snapshot() {
        return new HashMap<>(serviceInfoMap);
    }
    
    @Override
    public List<Class<? extends com.alibaba.nacos.common.notify.Event>> subscribeTypes() {
        List<Class<? extends com.alibaba.nacos.common.notify.Event>> result = new LinkedList<>();
        //TODO: service data change event, instance event
        result.add(ClientOperationEvent.ClientRegisterServiceEvent.class);
        result.add(ClientOperationEvent.ClientDeregisterServiceEvent.class);
        return result;
    }
    
    /**
     * description:onEvent.
     *
     * @param: event
     * @return: void
     */
    public void onEvent(com.alibaba.nacos.common.notify.Event event) {
        if (flagNotify) {
            flagNotify = false;
            cycleDebounce(new ToNotify());
        }
        //TODO: service or instance data change
        if (event instanceof ClientOperationEvent.ClientRegisterServiceEvent) {
            // If service changed, push to all subscribers.
            ClientOperationEvent.ClientRegisterServiceEvent clientRegisterServiceEvent = (ClientOperationEvent.ClientRegisterServiceEvent) event;
            Service service = clientRegisterServiceEvent.getService();
            
            String serviceName = IstioCrdUtil.buildServiceName(service);
            IstioService old = serviceInfoMap.get(serviceName);
            PushChange pushChange;
            
            if (old != null) {
                //instance change
                Loggers.MAIN.info("have old");
                //instance name is cate + . + instance id + . + service name,e.g
                pushChange = new PushChange("instance.51247." + serviceName, PushChange.ChangeType.UP);
            } else {
                Loggers.MAIN.info("have new");
                pushChange = new PushChange("service." + serviceName, PushChange.ChangeType.UP);
                pushChangeQueue.add(pushChange);
                pushChange = new PushChange("instance.51247." + serviceName, PushChange.ChangeType.UP);
            }
    
            serviceCache.put(serviceName, service);
            pushChangeQueue.add(pushChange);
            
        } else if (event instanceof ClientOperationEvent.ClientDeregisterServiceEvent) {
            ClientOperationEvent.ClientDeregisterServiceEvent clientDeregisterServiceEvent = (ClientOperationEvent
                    .ClientDeregisterServiceEvent) event;
            Service service = clientDeregisterServiceEvent.getService();
            String serviceName = IstioCrdUtil.buildServiceName(service);
            PushChange pushChange;
            
            if (serviceStorage.getPushData(service).ipCount() > 0) {
                Loggers.MAIN.info("remain instance");
                pushChange = new PushChange("instance.5666." + serviceName, PushChange.ChangeType.DOWN);
            } else {
                Loggers.MAIN.info("no instance left");
                pushChange = new PushChange("service." + serviceName, PushChange.ChangeType.DOWN);
                pushChangeQueue.add(pushChange);
                pushChange = new PushChange("instance.5666." + serviceName, PushChange.ChangeType.DOWN);
            }
    
            serviceCache.put(serviceName, service);
            pushChangeQueue.add(pushChange);
        }
    }
    
    private class ToNotify implements Runnable {
        @Override
        public void run() {
            while (true) {
                if (pushChangeQueue.size() > 0) {
                    DeltaResources updatePush;
                    Future<DeltaResources> futureUpdate = debouncePushChange(new Debounce(pushChangeQueue, istioConfig));
    
                    try {
                        updatePush = futureUpdate.get();
                        Loggers.MAIN.info("updatePush get!");
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
    
                    if (updatePush != null) {
                        Map<String, PushChange.ChangeType> serviceMap = updatePush.getServiceChangeMap();
                        Map<String, PushChange.ChangeType> instanceMap = updatePush.getInstanceChangeMap();
    
                        for (Map.Entry<String, PushChange.ChangeType> entry : serviceMap.entrySet()) {
                            String serviceName = entry.getKey();
                            PushChange.ChangeType changeType = entry.getValue();
                            Loggers.MAIN.info("service map entrySet:{serviceName:{}, changeType:{}}", serviceName, changeType);
                            updateServiceInfoMap(true, serviceName, changeType, updatePush);
                        }
    
                        for (Map.Entry<String, PushChange.ChangeType> entry : instanceMap.entrySet()) {
                            String serviceName = entry.getKey().split("\\.", 2)[1];
                            PushChange.ChangeType changeType = entry.getValue();
        
                            Loggers.MAIN.info("instance map entrySet:{serviceName:{}, changeType:{}}", serviceName, changeType);
                            updateServiceInfoMap(false, serviceName, changeType, updatePush);
                        }
    
                        Event event = new Event(serviceMap.size() != 0 ? EventType.Service : EventType.Endpoint, updatePush);
                        eventProcessor.notify(event);
                    } else {
                        Loggers.MAIN.info("updatePush is null");
                    }
                }
            }
        }
    }
    
    private void updateServiceInfoMap(boolean flagType, String serviceName, PushChange.ChangeType changeType, DeltaResources updatePush) {
        Service service = serviceCache.get(serviceName);
        Loggers.MAIN.info("type {} service {}", changeType, service.getGroupedServiceName());
        ServiceInfo serviceInfo = serviceStorage.getPushData(service);
        
        if (!serviceInfo.isValid()) {
            Loggers.MAIN.info("not valid");
            serviceInfoMap.remove(serviceName);
        }
    
        IstioService old = serviceInfoMap.get(serviceName);
        
        if (flagType) {
            if (serviceInfoMap.containsKey(serviceName)) {
                if (changeType == PushChange.ChangeType.UP || changeType == PushChange.ChangeType.DATA) {
                    if (old != null) {
                        Loggers.MAIN.info("infoMap old put");
                        serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo, old));
                    } else {
                        Loggers.MAIN.info("infoMap put");
                        serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
                    }
                } else {
                    Loggers.MAIN.info("infoMap remove !");
                    IstioService istioService = serviceInfoMap.get(serviceName);
            
                    //In fact, only a table can be processed, but in order to have more operations later, separate
                    String serviceEntryName = buildServiceEntryName(serviceName, istioConfig.getDomainSuffix(), istioService);
                    int port = (int) istioService.getPortsMap().values().toArray()[0];
                    String clusterName = buildClusterName(TrafficDirection.OUTBOUND, "",
                            serviceName + istioConfig.getDomainSuffix(), port);
            
                    updatePush.addRemovedClusterName(clusterName);
                    updatePush.addRemovedServiceEntryName(serviceName + "." + istioConfig.getDomainSuffix());
            
                    serviceInfoMap.remove(serviceName);
                }
            } else {
                Loggers.MAIN.info("new service");
                if (changeType == PushChange.ChangeType.UP || changeType == PushChange.ChangeType.DATA) {
                    serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
                } else {
                    Loggers.MAIN.info("no change");
                }
            }
        } else {
            if (old != null) {
                Loggers.MAIN.info("infoMap old put");
                serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo, old));
            } else {
                Loggers.MAIN.info("infoMap put");
                serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
            }
        }
    }
}