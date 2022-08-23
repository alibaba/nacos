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

package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushContext;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.google.protobuf.Any;
import istio.mcp.v1alpha1.MetadataOuterClass;
import istio.mcp.v1alpha1.ResourceOuterClass.Resource;
import istio.networking.v1alpha3.ServiceEntryOuterClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.nacos.istio.api.ApiConstants.SERVICE_ENTRY_PROTO;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildServiceEntry;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildServiceEntryName;

/**
 * @author special.fy
 */
public class ServiceEntryMcpGenerator implements ApiGenerator<Resource> {
    
    private List<ServiceEntryWrapper> serviceEntries;
    
    private static volatile ServiceEntryMcpGenerator singleton = null;

    public static ServiceEntryMcpGenerator getInstance() {
        if (singleton == null) {
            synchronized (ServiceEntryMcpGenerator.class) {
                if (singleton == null) {
                    singleton = new ServiceEntryMcpGenerator();
                }
            }
        }
        return singleton;
    }

    @Override
    public List<Resource> generate(PushContext pushContext) {
        List<Resource> result = new ArrayList<>();
        serviceEntries = new ArrayList<>(16);
        ResourceSnapshot resourceSnapshot = pushContext.getResourceSnapshot();
    
        IstioConfig istioConfig = resourceSnapshot.getIstioConfig();
        Map<String, IstioService> serviceInfoMap = resourceSnapshot.getIstioResources().getIstioServiceMap();
    
        for (Map.Entry<String, IstioService> entry : serviceInfoMap.entrySet()) {
            String serviceName = entry.getKey();
            String name = buildServiceEntryName(serviceName, istioConfig.getDomainSuffix(), entry.getValue());
            
            ServiceEntryWrapper serviceEntryWrapper = buildServiceEntry(serviceName, name, serviceInfoMap.get(serviceName));
            if (serviceEntryWrapper != null) {
                serviceEntries.add(serviceEntryWrapper);
            }
        }
        
        for (ServiceEntryWrapper serviceEntryWrapper : serviceEntries) {
            MetadataOuterClass.Metadata metadata = serviceEntryWrapper.getMetadata();
            ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryWrapper.getServiceEntry();

            Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(SERVICE_ENTRY_PROTO).build();

            result.add(Resource.newBuilder().setBody(any).setMetadata(metadata).build());
        }

        return result;
    }
    
    @Override
    public List<io.envoyproxy.envoy.service.discovery.v3.Resource> deltaGenerate(PushContext pushContext, Set<String> removed) {
        return new ArrayList<>();
    }
}
