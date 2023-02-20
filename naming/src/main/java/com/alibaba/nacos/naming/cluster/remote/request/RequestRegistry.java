/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.cluster.remote.request;

import com.alibaba.nacos.api.remote.Payload;
import com.alibaba.nacos.core.cluster.remote.request.AbstractClusterRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * payload registry.
 *
 * @author shiyiyue
 */
public class RequestRegistry {
    
    private static Set<Class<? extends Payload>> payloads = registryPayload();
    
    private static Set<Class<? extends Payload>> registryPayload() {
        HashSet<Class<? extends Payload>> payloads = new HashSet<>();
        payloads.add(AbstractClusterRequest.class);
        payloads.add(DistroDataRequest.class);
        return payloads;
    }
    
    public static final Set<Class<? extends Payload>> getPayloads() {
        return payloads;
    }
}
