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

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushContext;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.google.protobuf.Any;
import com.google.protobuf.ProtocolStringList;
import istio.mcp.v1alpha1.MetadataOuterClass.Metadata;
import istio.mcp.v1alpha1.ResourceOuterClass.Resource;
import istio.networking.v1alpha3.ServiceEntryOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass.ServiceEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.nacos.istio.api.ApiConstants.*;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildIstioServiceMapByInstance;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildIstioServiceMapByService;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildServiceEntry;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildServiceEntryName;

/**
 * @author special.fy
 */
public final class ServiceEntryXdsGenerator implements ApiGenerator<Any> {

    private static volatile ServiceEntryXdsGenerator singleton = null;
    
    private List<ServiceEntryWrapper> serviceEntries;

    public static ServiceEntryXdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (ServiceEntryXdsGenerator.class) {
                if (singleton == null) {
                    singleton = new ServiceEntryXdsGenerator();
                }
            }
        }
        return singleton;
    }

    @Override
    public List<Any> generate(PushContext pushContext) {
        List<Resource> resources = new ArrayList<>();
        serviceEntries = new ArrayList<>(16);
        IstioConfig istioConfig = pushContext.getResourceSnapshot().getIstioConfig();
        Map<String, IstioService> serviceInfoMap = pushContext.getResourceSnapshot().getIstioResources().getIstioServiceMap();
    
        for (Map.Entry<String, IstioService> entry : serviceInfoMap.entrySet()) {
            String serviceName = entry.getKey();
            String name = buildServiceEntryName(serviceName, istioConfig.getDomainSuffix(), entry.getValue());
            
            ServiceEntryWrapper serviceEntryWrapper = buildServiceEntry(serviceName, name, serviceInfoMap.get(serviceName));
            if (serviceEntryWrapper != null) {
                serviceEntries.add(serviceEntryWrapper);
            }
        }
        for (ServiceEntryWrapper serviceEntryWrapper : serviceEntries) {
            Metadata metadata = serviceEntryWrapper.getMetadata();
            ServiceEntry serviceEntry = serviceEntryWrapper.getServiceEntry();
        
            Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(SERVICE_ENTRY_PROTO).build();
        
            resources.add(Resource.newBuilder().setBody(any).setMetadata(metadata).build());
        }
    
        List<Any> result = new ArrayList<>();
        for (Resource resource : resources) {
            result.add(Any.newBuilder().setValue(resource.toByteString()).setTypeUrl(MCP_RESOURCE_PROTO).build());
        }
    
        return result;
    }
    
    @Override
    public List<io.envoyproxy.envoy.service.discovery.v3.Resource> deltaGenerate(PushContext pushContext, Set<String> removed) {
        List<io.envoyproxy.envoy.service.discovery.v3.Resource> result = new ArrayList<>();
        serviceEntries = new ArrayList<>();
        IstioConfig istioConfig = pushContext.getResourceSnapshot().getIstioConfig();
        Map<String, IstioService> istioServiceMap = buildIstioServiceMapByService(pushContext);
        Set<String> removedHostName = pushContext.getResourceSnapshot().getRemovedServiceEntryName();
        buildIstioServiceMapByInstance(pushContext).forEach((key, value) -> istioServiceMap.merge(key, value, (v1, v2) -> v1));
        ProtocolStringList subscribe = pushContext.getResourceNamesSubscribe();
    
        for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
            String serviceName = entry.getKey();
            String name = buildServiceEntryName(serviceName, istioConfig.getDomainSuffix(), entry.getValue());
            
            if (subscribe.contains(name)) {
                ServiceEntryWrapper serviceEntryWrapper = buildServiceEntry(serviceName, name, istioServiceMap.get(serviceName));
                if (serviceEntryWrapper != null) {
                    serviceEntries.add(serviceEntryWrapper);
                } else {
                    removed.add(name);
                }
                subscribe.remove(name);
            }
        }
        
        for (String restName : subscribe) {
            if (removedHostName.contains(restName)) {
                removed.add(restName);
            }
        }
        
        for (ServiceEntryWrapper serviceEntryWrapper : serviceEntries) {
            ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryWrapper.getServiceEntry();

            Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(SERVICE_ENTRY_PROTO).build();

            result.add(io.envoyproxy.envoy.service.discovery.v3.Resource.newBuilder().setResource(any).setVersion(
                    pushContext.getVersion()).build());
        }
        
        return result;
    }
}
