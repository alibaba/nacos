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
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.util.IstioCrdUtil;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NacosServiceInfoResourceWatcher.
 *
 * @author special.fy
 */
@org.springframework.stereotype.Service
public class NacosServiceInfoResourceWatcher extends SmartSubscriber {

    private final Map<String, IstioService> serviceInfoMap = new ConcurrentHashMap<>(16);

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
        result.add(ServiceEvent.ServiceChangedEvent.class);
        return result;
    }
    
    /**
     * description:onEvent.
     * @param: event
     * @return: void
     */
    public void onEvent(com.alibaba.nacos.common.notify.Event event) {
        if (event instanceof ServiceEvent.ServiceChangedEvent) {
            // If service changed, push to all subscribers.
            ServiceEvent.ServiceChangedEvent serviceChangedEvent = (ServiceEvent.ServiceChangedEvent) event;
            Service service = serviceChangedEvent.getService();
            
            try {
                handleEvent(service);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * description:handleEvent.
     * @param: [service]
     * @return: void
     */
    public void handleEvent(Service eventService) throws IOException {
        String namespace = eventService.getNamespace();
        Set<Service> services = ServiceManager.getInstance().getSingletons(namespace);
        Set<String> allServices = new HashSet<>();
        
        for (Service service : services) {
            String serviceName = IstioCrdUtil.buildServiceNameForServiceEntry(service);
            allServices.add(serviceName);
    
            IstioService old = serviceInfoMap.get(serviceName);
            // Service not changed
            if (old != null && old.getRevision().equals(service.getRevision())) {
                continue;
            }
            // Update the resource
            ServiceInfo serviceInfo = serviceStorage.getPushData(service);
            if (!serviceInfo.isValid()) {
                serviceInfoMap.remove(serviceName);
            }
    
            if (old != null) {
                serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo, old));
                Loggers.MAIN.info("ChangeService: {}", service.toString());
            } else {
                serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
                Loggers.MAIN.info("NewService: {}", service.toString());
            }
        }
        
        for (String key : serviceInfoMap.keySet()) {
            if (!allServices.contains(key)) {
                serviceInfoMap.remove(key);
            }
        }
        
        eventProcessor.notify(Event.SERVICE_UPDATE_EVENT);
    }
}
