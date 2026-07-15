/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrpcServerConstantsTest {
    
    @Test
    void testGrpcConfigConstants() throws Exception {
        Class<?> grpcConfig =
            Class.forName("com.alibaba.nacos.core.remote.grpc.GrpcServerConstants$GrpcConfig");
        Field maxInbound = grpcConfig.getDeclaredField("DEFAULT_GRPC_MAX_INBOUND_MSG_SIZE");
        maxInbound.setAccessible(true);
        assertEquals(10 * 1024 * 1024, maxInbound.get(null));
        Field permitKeepAlive = grpcConfig.getDeclaredField("DEFAULT_GRPC_PERMIT_KEEP_ALIVE_TIME");
        permitKeepAlive.setAccessible(true);
        assertNotNull(permitKeepAlive.get(null));
    }
}
