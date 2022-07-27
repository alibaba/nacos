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
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.google.protobuf.Any;
import io.envoyproxy.envoy.api.v2.Cluster;
import io.envoyproxy.envoy.config.core.v3.HttpProtocolOptions;
import istio.mcp.v1alpha1.MetadataOuterClass;
import istio.mcp.v1alpha1.ResourceOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass;
import istio.networking.v1alpha3.WorkloadEntryOuterClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.istio.api.ApiConstants.CLUSTER_TYPE;
import static com.alibaba.nacos.istio.api.ApiConstants.HttpProtocolOptionsType;

/**
 * CdsGenerator.
 * @Author RocketEngine26
 * @Date 2022/7/24 15:28
 */
public final class CdsGenerator implements ApiGenerator<Any> {
    
    private static volatile CdsGenerator singleton = null;
    
    public static CdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (ServiceEntryXdsGenerator.class) {
                if (singleton == null) {
                    singleton = new CdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(ResourceSnapshot resourceSnapshot) {
        List<ResourceOuterClass.Resource> resources = new ArrayList<>();
        
        List<ServiceEntryWrapper> serviceEntries = resourceSnapshot.getServiceEntries();
        for (ServiceEntryWrapper serviceEntryWrapper : serviceEntries) {
            MetadataOuterClass.Metadata metadata = serviceEntryWrapper.getMetadata();
            ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryWrapper.getServiceEntry();
            
            List<WorkloadEntryOuterClass.WorkloadEntry> workloadEntries = serviceEntry.getEndpointsList();
            Map<String, Cluster> clusters = new HashMap<>(serviceEntry.getEndpointsCount());
            //逻辑是，针对每一个workloadentry根据其clustername，进行分类提取
            for (WorkloadEntryOuterClass.WorkloadEntry workloadEntry : workloadEntries) {
                String clusterName = workloadEntry.getLabelsMap().get("cluster");
                if (!clusters.containsKey(clusterName)) {
                    //貌似现在还没有实现我们这个包里，但是可以设置那些已经废弃的接口，很奇怪，有typedExtensionProtocolOptions，但是其相应的变量没有
                    HttpProtocolOptions options = HttpProtocolOptions.newBuilder().build();
                    Map<String, Any> typedExtensionProtocolOptions = new HashMap<>();
                    typedExtensionProtocolOptions.put(HttpProtocolOptionsType, Any.newBuilder().setValue(options.toByteString()).build());
                    
                    Cluster cluster = Cluster.newBuilder().setName(clusterName).setType(Cluster.DiscoveryType.EDS)
                            .putAllTypedExtensionProtocolOptions(typedExtensionProtocolOptions).build();
                    
                    clusters.put(clusterName, cluster);
                }
            }
            for (Map.Entry<String, Cluster> entry : clusters.entrySet()) {
                //Labels,CreateTime,Version?
                MetadataOuterClass.Metadata clusterMetadata = MetadataOuterClass.Metadata.newBuilder()
                        .setName(metadata.getName() + "/" + entry.getKey())
                        .putAnnotations("virtual", "1")
                        .putLabels("ClusterType", "nacos")
                        .setCreateTime(metadata.getCreateTime())
                        .setVersion(metadata.getVersion()).build();
                //CLUSTER_TYPE?
                Any any = Any.newBuilder().setValue(entry.getValue().toByteString()).setTypeUrl(CLUSTER_TYPE).build();
                resources.add(ResourceOuterClass.Resource.newBuilder().setBody(any).setMetadata(clusterMetadata).build());
            }
        }
        
        List<Any> result = new ArrayList<>();
        for (ResourceOuterClass.Resource resource : resources) {
            result.add(Any.newBuilder().setValue(resource.toByteString()).setTypeUrl(CLUSTER_TYPE).build());
        }
        
        return result;
    }
}

