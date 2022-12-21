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

package com.alibaba.nacos.api.remote.response;

import com.alibaba.nacos.api.remote.Payload;

import java.util.HashSet;
import java.util.Set;

/**
 * payload registry.
 *
 * @author shiyiyue
 */
public class ResponseRegistry {
    
    private static Set<Class<? extends Payload>> payloads = getPayload();
    
    private static Set<Class<? extends Payload>> getPayload() {
        HashSet<Class<? extends Payload>> payloads = new HashSet<>();
        payloads.add(Response.class);
        payloads.add(ClientDetectionResponse.class);
        payloads.add(ConnectResetResponse.class);
        payloads.add(ErrorResponse.class);
        payloads.add(HealthCheckResponse.class);
        payloads.add(ServerCheckResponse.class);
        payloads.add(ServerLoaderInfoResponse.class);
        payloads.add(ServerReloadResponse.class);
        return payloads;
    }
    
    public static final Set<Class<? extends Payload>> getPayloads() {
        return payloads;
    }
}
