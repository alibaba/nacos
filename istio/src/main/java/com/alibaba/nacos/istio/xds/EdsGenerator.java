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
import istio.mcp.v1alpha1.MetadataOuterClass;
import istio.mcp.v1alpha1.ResourceOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.istio.api.ApiConstants.MCP_RESOURCE_PROTO;
import static com.alibaba.nacos.istio.api.ApiConstants.SERVICE_ENTRY_PROTO;

/**
 * EdsGenerator.
 * @Author RocketEngine26
 * @Date 2022/7/24 15:28
 */
public final class EdsGenerator implements ApiGenerator<Any> {
   
    private static volatile EdsGenerator singleton = null;
    
    public static EdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (ServiceEntryXdsGenerator.class) {
                if (singleton == null) {
                    singleton = new EdsGenerator();
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
            
            Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(SERVICE_ENTRY_PROTO).build();
            
            resources.add(ResourceOuterClass.Resource.newBuilder().setBody(any).setMetadata(metadata).build());
        }
        
        List<Any> result = new ArrayList<>();
        for (ResourceOuterClass.Resource resource : resources) {
            result.add(Any.newBuilder().setValue(resource.toByteString()).setTypeUrl(MCP_RESOURCE_PROTO).build());
        }
        
        return result;
    }
}