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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.remote.client.RpcClientConfigFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import com.alibaba.nacos.common.remote.client.grpc.GrpcSdkClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcPortOffsetClientPropertiesTest {
    
    private GrpcSdkClient grpcSdkClient;
    
    @AfterEach
    void tearDown() throws NacosException {
        System.clearProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY);
        if (grpcSdkClient != null) {
            grpcSdkClient.shutdown();
        }
    }
    
    @Test
    void testClientPropertiesGrpcPortOffsetOverridesSystemProperty() {
        System.setProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY, "10000");
        
        Properties properties = new Properties();
        properties.setProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY, "1002");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        GrpcClientConfig grpcClientConfig = RpcClientConfigFactory.getInstance()
            .createGrpcClientConfig(clientProperties.asProperties(), Collections.emptyMap());
        
        grpcSdkClient = new GrpcSdkClient(grpcClientConfig);
        
        assertEquals(1002, grpcSdkClient.rpcPortOffset());
    }
}
