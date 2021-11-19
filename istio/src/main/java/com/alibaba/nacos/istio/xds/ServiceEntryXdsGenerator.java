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
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.google.protobuf.Any;
import istio.mcp.v1alpha1.MetadataOuterClass.Metadata;
import istio.mcp.v1alpha1.ResourceOuterClass.Resource;
import istio.networking.v1alpha3.ServiceEntryOuterClass.ServiceEntry;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.istio.api.ApiConstants.*;

/**
 * @author special.fy
 */
public final class ServiceEntryXdsGenerator implements ApiGenerator<Any> {

    private volatile static ServiceEntryXdsGenerator singleton = null;

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
    public List<Any> generate(ResourceSnapshot resourceSnapshot) {
        List<Resource> resources = new ArrayList<>();

        List<ServiceEntryWrapper> serviceEntries = resourceSnapshot.getServiceEntries();
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
}
