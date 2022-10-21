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
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushRequest;
import com.google.protobuf.Any;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.core.v3.AggregatedConfigSource;
import io.envoyproxy.envoy.config.core.v3.ConfigSource;
import io.envoyproxy.envoy.config.core.v3.Http1ProtocolOptions;
import io.envoyproxy.envoy.config.core.v3.Http2ProtocolOptions;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import io.envoyproxy.envoy.service.discovery.v3.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.istio.api.ApiConstants.CLUSTER_TYPE;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildClusterName;
import static io.envoyproxy.envoy.config.core.v3.ApiVersion.V2_VALUE;

/**
 * @author RocketEngine26
 * @date 2022/8/17
 */
public final class CdsGenerator implements ApiGenerator<Any> {
    
    private static volatile CdsGenerator singleton = null;
    
    public static CdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (CdsGenerator.class) {
                if (singleton == null) {
                    singleton = new CdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(PushRequest pushRequest) {
        if (!pushRequest.isFull()) {
            return null;
        }
        List<Any> result = new ArrayList<>();
        IstioConfig istioConfig = pushRequest.getResourceSnapshot().getIstioConfig();
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources().getIstioServiceMap();
        for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
            String name = buildClusterName(TrafficDirection.OUTBOUND, "",
                    entry.getKey() + '.' +  istioConfig.getDomainSuffix(), entry.getValue().getPort());
            
            Cluster.Builder cluster = Cluster.newBuilder().setName(name).setType(Cluster.DiscoveryType.EDS)
                    .setEdsClusterConfig(Cluster.EdsClusterConfig.newBuilder().setServiceName(name).setEdsConfig(
                            ConfigSource.newBuilder().setAds(AggregatedConfigSource.newBuilder())
                                    .setResourceApiVersionValue(V2_VALUE).build()).build());
            if ("grpc".equals(entry.getValue().getProtocol())) {
                cluster.setHttp2ProtocolOptions(Http2ProtocolOptions.newBuilder().build());
            } else {
                cluster.setHttpProtocolOptions(Http1ProtocolOptions.newBuilder().build());
            }
    
            result.add(Any.newBuilder().setValue(cluster.build().toByteString()).setTypeUrl(CLUSTER_TYPE).build());
        }
        
        return result;
    }
    
    @Override
    public List<Resource> deltaGenerate(PushRequest pushRequest) {
        Loggers.MAIN.info("Delta Cds Not supported");
        return null;
    }
}