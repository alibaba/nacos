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
import com.alibaba.nacos.istio.model.PushRequest;
import com.google.protobuf.Any;
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
import static com.alibaba.nacos.istio.util.IstioCrdUtil.parseClusterNameToServiceName;

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
    public List<Any> generate(PushRequest pushRequest) {
        List<Any> result = new ArrayList<>();
        IstioConfig istioConfig = pushRequest.getResourceSnapshot().getIstioConfig();
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources().getIstioServiceMap();
        if (pushRequest.getReason().size() != 0) {
            for (String reason : pushRequest.getReason()) {
                IstioService istioService = istioServiceMap.get(reason);
                String name = buildClusterName(TrafficDirection.OUTBOUND, "",
                        reason + '.' + istioConfig.getDomainSuffix(), istioService.getPort());
                Any any = buildEndpoint(name, istioService);
                if (any != null) {
                    result.add(any);
                }
            }
        } else {
            for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
                String name = buildClusterName(TrafficDirection.OUTBOUND, "",
                        entry.getKey() + '.' + istioConfig.getDomainSuffix(), entry.getValue().getPort());
                Any any = buildEndpoint(name, entry.getValue());
                if (any != null) {
                    result.add(any);
                }
            }
        }
        return result;
    }
    
    @Override
    public List<Resource> deltaGenerate(PushRequest pushRequest) {
        if (pushRequest.isFull()) {
            return null;
        }
        
        List<Resource> result = new ArrayList<>();
        Set<String> reason = pushRequest.getReason();
        IstioConfig istioConfig = pushRequest.getResourceSnapshot().getIstioConfig();
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources().getIstioServiceMap();
        
        if (pushRequest.getSubscribe().size() != 0) {
            for (String subscribe : pushRequest.getSubscribe()) {
                String serviceName = parseClusterNameToServiceName(subscribe, istioConfig.getDomainSuffix());
                if (reason.contains(serviceName)) {
                    if (istioServiceMap.containsKey(serviceName)) {
                        Any any = buildEndpoint(subscribe, istioServiceMap.get(serviceName));
                        if (any != null) {
                            result.add(Resource.newBuilder().setResource(any).setVersion(pushRequest.getResourceSnapshot().getVersion()).build());
                        } else {
                            pushRequest.addRemoved(subscribe);
                        }
                    } else {
                        pushRequest.addRemoved(subscribe);
                    }
                }
            }
        } else {
            for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
                String name = buildClusterName(TrafficDirection.OUTBOUND, "",
                        entry.getKey() + '.' + istioConfig.getDomainSuffix(), entry.getValue().getPort());
                Any any = buildEndpoint(name, entry.getValue());
                if (any != null) {
                    result.add(Resource.newBuilder().setResource(any).setVersion(pushRequest.getResourceSnapshot().getVersion()).build());
                } else {
                    pushRequest.addRemoved(name);
                }
            }
        }
        
        return result;
    }
    
    private static Any buildEndpoint(String name, IstioService istioService) {
        if (istioService.getHosts().isEmpty()) {
            return null;
        }
        
        List<IstioEndpoint> istioEndpoints = istioService.getHosts();
        Map<String, LocalityLbEndpoints.Builder> llbEndpointsBuilder = new HashMap<>(istioEndpoints.size());
    
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
        
        if (listlle.size() == 0) {
            return null;
        }
        
        ClusterLoadAssignment cla = ClusterLoadAssignment.newBuilder().setClusterName(name).addAllEndpoints(listlle).build();
        return Any.newBuilder().setValue(cla.toByteString()).setTypeUrl(ENDPOINT_TYPE).build();
    }
}