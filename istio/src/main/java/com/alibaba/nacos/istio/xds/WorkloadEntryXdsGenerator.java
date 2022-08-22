/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushContext;
import com.google.protobuf.Any;
import com.google.protobuf.ProtocolStringList;
import io.envoyproxy.envoy.service.discovery.v3.Resource;
import istio.mcp.v1alpha1.ResourceOuterClass;
import istio.networking.v1alpha3.WorkloadEntryOuterClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.nacos.istio.api.ApiConstants.MCP_RESOURCE_PROTO;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildIstioServiceMapByInstance;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildWorkloadEntry;

/**
 * @author RocketEngine26
 * @date 2022/8/21 下午2:47
 */
public class WorkloadEntryXdsGenerator implements ApiGenerator<Any> {
    private static volatile WorkloadEntryXdsGenerator singleton = null;
    
    public static WorkloadEntryXdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (WorkloadEntryXdsGenerator.class) {
                if (singleton == null) {
                    singleton = new WorkloadEntryXdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(PushContext pushContext) {
        //TODO:for workload entry ,this resource right ?
        List<ResourceOuterClass.Resource> resources = new ArrayList<>();
        List<WorkloadEntryOuterClass.WorkloadEntry> endpoints = new ArrayList<>();
        Map<String, IstioService> serviceInfoMap = pushContext.getResourceSnapshot().getIstioResources().getIstioServiceMap();
        
        for (IstioService istioService : serviceInfoMap.values()) {
            endpoints.addAll(buildWorkloadEntry(istioService.getHosts()));
        }
        
        for (WorkloadEntryOuterClass.WorkloadEntry workloadEntry : endpoints) {
            //TODO: type url
            Any any = Any.newBuilder().setValue(workloadEntry.toByteString()).setTypeUrl("Workload_ENTRY_PROTO").build();
    
            resources.add(ResourceOuterClass.Resource.newBuilder().setBody(any).build());
        }
    
        List<Any> result = new ArrayList<>();
        for (ResourceOuterClass.Resource resource : resources) {
            //TODO:type url
            result.add(Any.newBuilder().setValue(resource.toByteString()).setTypeUrl(MCP_RESOURCE_PROTO).build());
        }
        
        return result;
    }
    
    @Override
    public List<Resource> deltaGenerate(PushContext pushContext, Set<String> removed) {
        List<Resource> result = new ArrayList<>();
        List<WorkloadEntryOuterClass.WorkloadEntry> endpoints = new ArrayList<>();
        Map<String, IstioService> serviceInfoMap = buildIstioServiceMapByInstance(pushContext);
        ProtocolStringList subscribe = pushContext.getResourceNamesSubscribe();
    
        for (Map.Entry<String, IstioService> entry : serviceInfoMap.entrySet()) {
            //TODO: name
            if (subscribe.contains(entry.getKey())) {
                endpoints.addAll(buildWorkloadEntry(entry.getValue().getHosts()));
            }
        }
        
        for (WorkloadEntryOuterClass.WorkloadEntry workloadEntry : endpoints) {
            //TODO: type url
            Any any = Any.newBuilder().setValue(workloadEntry.toByteString()).setTypeUrl("Delta_Workload_ENTRY_PROTO").build();
        
            result.add(Resource.newBuilder().setResource(any).setVersion(pushContext.getVersion()).build());
        }
        
        return result;
    }
}
