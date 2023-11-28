/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class GrpcSdkClientTest {
    
    GrpcSdkClient grpcSdkClient;
    
    @After
    public void tearDown() throws NacosException {
        System.clearProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY);
        if (grpcSdkClient != null) {
            grpcSdkClient.shutdown();
        }
    }
    
    @Test
    public void testAbilityMode() {
        grpcSdkClient = new GrpcSdkClient("test");
        assertEquals(AbilityMode.SDK_CLIENT, grpcSdkClient.abilityMode());
    }
    
    @Test
    public void testRpcPortOffsetDefault() {
        grpcSdkClient = new GrpcSdkClient(new Properties());
        assertEquals(1000, grpcSdkClient.rpcPortOffset());
    }
    
    @Test
    public void testRpcPortOffsetFromSystemProperty() {
        System.setProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY, "10000");
        grpcSdkClient = new GrpcSdkClient("test", 8, 8, Collections.emptyMap());
        assertEquals(10000, grpcSdkClient.rpcPortOffset());
    }
    
    @Test
    public void testGrpcClientByConfig() {
        GrpcClientConfig config = DefaultGrpcClientConfig.newBuilder().setName("test111").build();
        grpcSdkClient = new GrpcSdkClient(config);
        assertEquals("test111", grpcSdkClient.getName());
    }
}