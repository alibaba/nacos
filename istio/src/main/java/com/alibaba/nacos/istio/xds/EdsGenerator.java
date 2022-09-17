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
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.model.IstioEndpoint;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushContext;
import com.google.protobuf.Any;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.UInt32Value;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LocalityLbEndpoints;
import io.envoyproxy.envoy.service.discovery.v3.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.nacos.istio.api.ApiConstants.ENDPOINT_TYPE;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildClusterName;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildIstioServiceMapByInstance;

/**.
 * @author RocketEngine26
 * @date 2022/7/24 15:28
 */
public final class EdsGenerator implements ApiGenerator<Any> {
    
    private static volatile EdsGenerator singleton = null;
    
    public static EdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (EdsGenerator.class) {
                if (singleton == null) {
                    singleton = new EdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(PushContext pushContext) {
        IstioConfig istioConfig = pushContext.getResourceSnapshot().getIstioConfig();
        Map<String, IstioService> istioServiceMap = buildIstioServiceMapByInstance(pushContext);
        
        return buildEndpoints(istioServiceMap, istioConfig.getDomainSuffix(), null);
    }
    
    @Override
    public List<Resource> deltaGenerate(PushContext pushContext, Set<String> removed) {
        List<Resource> result = new ArrayList<>();
        IstioConfig istioConfig = pushContext.getResourceSnapshot().getIstioConfig();
        Set<String> removedClusterName = pushContext.getResourceSnapshot().getRemovedClusterName();
        Map<String, IstioService> istioServiceMap = buildIstioServiceMapByInstance(pushContext);
        ProtocolStringList subscribe = pushContext.getResourceNamesSubscribe();
        
        for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
            String serviceName = entry.getKey();
            int port = (int) entry.getValue().getPortsMap().values().toArray()[0];
            String name = buildClusterName(TrafficDirection.OUTBOUND, "",
                    serviceName + '.' + istioConfig.getDomainSuffix(), port);
            if (!subscribe.contains(name)) {
                istioServiceMap.remove(serviceName);
            }
        }
        
        for (String removedName : subscribe) {
            if (removedClusterName.contains(removedName)) {
                removed.add(removedName);
            }
        }
        
        List<Any> temp = buildEndpoints(istioServiceMap, istioConfig.getDomainSuffix(), removed);
        
        for (Any any : temp) {
            result.add(Resource.newBuilder().setResource(any).setVersion(pushContext.getVersion()).build());
        }
    
        return result;
    }
    
    private static List<Any> buildEndpoints(Map<String, IstioService> istioServiceMap, String domain, Set<String> removed) {
        List<Any> result = new ArrayList<>();
        
        for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
            List<IstioEndpoint> istioEndpoints = entry.getValue().getHosts();
            Map<String, LocalityLbEndpoints.Builder> llbEndpointsBuilder = new HashMap<>(istioEndpoints.size());
        
            int port = (int) entry.getValue().getPortsMap().values().toArray()[0];
            String name = buildClusterName(TrafficDirection.OUTBOUND, "",
                    entry.getKey() + '.' + domain, port);
        
            for (IstioEndpoint istioEndpoint : istioEndpoints) {
                String label = istioEndpoint.getStringLocality();
                LbEndpoint lbEndpoint = istioEndpoint.getLbEndpoint();
            
                if (!llbEndpointsBuilder.containsKey(label)) {
                    LocalityLbEndpoints.Builder llbEndpointBuilder = LocalityLbEndpoints.newBuilder()
                            .setLocality(istioEndpoint.getLocality()).addLbEndpoints(lbEndpoint);
                    llbEndpointsBuilder.put(label, llbEndpointBuilder);
                } else {
                    llbEndpointsBuilder.get(label).addLbEndpoints(lbEndpoint);
                }
            }
        
            List<LocalityLbEndpoints> listlle = new ArrayList<>();
            for (LocalityLbEndpoints.Builder builder : llbEndpointsBuilder.values()) {
                int weight = 0;
                for (LbEndpoint lbEndpoint : builder.getLbEndpointsList()) {
                    weight += lbEndpoint.getLoadBalancingWeight().getValue();
                }
                LocalityLbEndpoints lle = builder.setLoadBalancingWeight(UInt32Value.newBuilder().setValue(weight)).build();
                listlle.add(lle);
            }
            
            if (listlle.size() == 0 && removed != null) {
                removed.add(name);
                continue;
            }
            
            ClusterLoadAssignment cla = ClusterLoadAssignment.newBuilder().setClusterName(name).addAllEndpoints(listlle).build();
            Any any = Any.newBuilder().setValue(cla.toByteString()).setTypeUrl(ENDPOINT_TYPE).build();
            result.add(any);
        }
        
        return result;
    }
}