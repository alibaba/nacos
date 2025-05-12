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
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushRequest;
import com.alibaba.nacos.istio.util.IstioCrdUtil;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.metadata.InfoChangeEvent;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.alibaba.nacos.istio.util.IstioExecutor.cycleDebounce;
import static com.alibaba.nacos.istio.util.IstioExecutor.debouncePushChange;

/**
 * @author special.fy
 */
@org.springframework.stereotype.Service
public class NacosServiceInfoResourceWatcher extends SmartSubscriber {

    private final Map<String, IstioService> serviceInfoMap = new ConcurrentHashMap<>(16);
    
    private final Queue<PushRequest> pushRequestQueue = new ConcurrentLinkedQueue<>();
    
    private boolean isInitial = true;
    
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
        result.add(ClientOperationEvent.ClientRegisterServiceEvent.class);
        result.add(ClientOperationEvent.ClientDeregisterServiceEvent.class);
        result.add(InfoChangeEvent.ServiceInfoChangeEvent.class);
        result.add(InfoChangeEvent.InstanceInfoChangeEvent.class);
        return result;
    }
    
    public void onEvent(com.alibaba.nacos.common.notify.Event event) {
        if (isInitial) {
            init();
            isInitial = false;
            cycleDebounce(new ToNotify());
        }
        
        if (event instanceof ClientOperationEvent.ClientRegisterServiceEvent) {
            // If service changed, push to all subscribers.
            ClientOperationEvent.ClientRegisterServiceEvent clientRegisterServiceEvent = (ClientOperationEvent.ClientRegisterServiceEvent) event;
            Service service = clientRegisterServiceEvent.getService();
            String serviceName = IstioCrdUtil.buildServiceName(service);
            
            IstioService old = serviceInfoMap.get(serviceName);
            PushRequest pushRequest;
            
            boolean full = update(serviceName, service);
            if (old != null) {
                pushRequest = new PushRequest(serviceName, full);
            } else {
                pushRequest = new PushRequest(serviceName, true);
            }
            pushRequestQueue.add(pushRequest);
        } else if (event instanceof ClientOperationEvent.ClientDeregisterServiceEvent) {
            ClientOperationEvent.ClientDeregisterServiceEvent clientDeregisterServiceEvent = (ClientOperationEvent
                    .ClientDeregisterServiceEvent) event;
            Service service = clientDeregisterServiceEvent.getService();
            String serviceName = IstioCrdUtil.buildServiceName(service);
            PushRequest pushRequest;
    
            boolean full = update(serviceName, service);
            if (serviceStorage.getPushData(service).ipCount() <= 0) {
                pushRequest = new PushRequest(serviceName, true);
                serviceInfoMap.remove(serviceName);
            } else {
                pushRequest = new PushRequest(serviceName, full);
            }
            pushRequestQueue.add(pushRequest);
        } else if (event instanceof InfoChangeEvent.ServiceInfoChangeEvent) {
            InfoChangeEvent.ServiceInfoChangeEvent serviceInfoChangeEvent = (InfoChangeEvent.ServiceInfoChangeEvent) event;
            Service service = serviceInfoChangeEvent.getService();
            String serviceName = IstioCrdUtil.buildServiceName(service);
            PushRequest pushRequest = new PushRequest(serviceName, true);
            
            update(serviceName, service);
            pushRequestQueue.add(pushRequest);
            
        } else if (event instanceof InfoChangeEvent.InstanceInfoChangeEvent) {
            InfoChangeEvent.InstanceInfoChangeEvent instanceInfoChangeEvent = (InfoChangeEvent.InstanceInfoChangeEvent) event;
            Service service = instanceInfoChangeEvent.getService();
            String serviceName = IstioCrdUtil.buildServiceName(service);
    
            boolean full = update(serviceName, service);
            PushRequest pushRequest = new PushRequest(serviceName, full);
            pushRequestQueue.add(pushRequest);
        }
    }
    
    private void init() {
        Set<String> namespaces =  ServiceManager.getInstance().getAllNamespaces();
        for (String namespace : namespaces) {
            Set<Service> services = ServiceManager.getInstance().getSingletons(namespace);
            if (services.isEmpty()) {
                continue;
            }
        
            for (Service service : services) {
                String serviceName = IstioCrdUtil.buildServiceName(service);
                ServiceInfo serviceInfo = serviceStorage.getPushData(service);
                if (!serviceInfo.isValid()) {
                    continue;
                }
                serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
                pushRequestQueue.add(new PushRequest(serviceName, true));
            }
        }
    }
    
    private boolean update(String serviceName, Service service) {
        ServiceInfo serviceInfo = serviceStorage.getPushData(service);
        if (!serviceInfo.isValid()) {
            serviceInfoMap.remove(serviceName);
            return true;
        }
        
        IstioService old = serviceInfoMap.get(serviceName);
        if (old != null) {
            serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo, old));
        } else {
            serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
        }
        return false;
    }
    
    private class ToNotify implements Runnable {
        @Override
        public void run() {
            while (true) {
                if (pushRequestQueue.size() > 0) {
                    PushRequest updatePush;
                    Future<PushRequest> futureUpdate = debouncePushChange(new Debounce(pushRequestQueue, istioConfig));
    
                    try {
                        updatePush = futureUpdate.get();
                        if (updatePush != null) {
                            eventProcessor.notify(updatePush);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}