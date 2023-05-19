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
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.util.IstioCrdUtil;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author special.fy
 */
@org.springframework.stereotype.Service
public class NacosServiceInfoResourceWatcher implements Runnable {

    private final Map<String, IstioService> serviceInfoMap = new ConcurrentHashMap<>(16);

    @Autowired
    private ServiceStorage serviceStorage;

    @Autowired
    private EventProcessor eventProcessor;

    @Override
    public void run() {
        boolean changed = false;

        // Query all services to see if any of them have changes.
        Set<String> namespaces =  ServiceManager.getInstance().getAllNamespaces();
        Set<String> allServices = new HashSet<>();
        for (String namespace : namespaces) {
            Set<Service>  services = ServiceManager.getInstance().getSingletons(namespace);
            if (services.isEmpty()) {
                continue;
            }

            for (Service service : services) {
                String serviceName = IstioCrdUtil.buildServiceNameForServiceEntry(service);
                allServices.add(serviceName);

                IstioService old = serviceInfoMap.get(serviceName);
                // Service not changed
                if (old != null && old.getRevision().equals(service.getRevision())) {
                    continue;
                }

                // Update the resource
                changed = true;
                ServiceInfo serviceInfo = serviceStorage.getPushData(service);
                if (!serviceInfo.isValid()) {
                    serviceInfoMap.remove(serviceName);
                    continue;
                }

                if (old != null) {
                    serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo, old));
                } else {
                    serviceInfoMap.put(serviceName, new IstioService(service, serviceInfo));
                }
            }
        }

        for (String key : serviceInfoMap.keySet()) {
            if (!allServices.contains(key)) {
                changed = true;
                serviceInfoMap.remove(key);
            }
        }

        if (changed) {
            eventProcessor.notify(Event.SERVICE_UPDATE_EVENT);
        }
    }

    public Map<String, IstioService> snapshot() {
        return new HashMap<>(serviceInfoMap);
    }
}
