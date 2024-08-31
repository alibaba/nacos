/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GrpcSdkServer} and {@link GrpcClusterServer} unit test.
 *
 * @author chenglu
 * @date 2021-06-30 14:32
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class GrpcServerTest {
    
    static MockedStatic<ApplicationUtils> applicationUtilsMockedStatic = null;
    
    private BaseGrpcServer grpcSdkServer;
    
    @BeforeAll
    static void setUpBeforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
    }
    
    @AfterAll
    static void after() {
        applicationUtilsMockedStatic.close();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (null != grpcSdkServer) {
            grpcSdkServer.stopServer();
        }
    }
    
    @Test
    void testGrpcSdkServer() throws Exception {
        grpcSdkServer = new GrpcSdkServer();
        grpcSdkServer.start();
        assertEquals(ConnectionType.GRPC, grpcSdkServer.getConnectionType());
        assertEquals(1000, grpcSdkServer.rpcPortOffset());
    }
    
    @Test
    void testGrpcClusterServer() throws Exception {
        grpcSdkServer = new GrpcClusterServer();
        grpcSdkServer.start();
        assertEquals(ConnectionType.GRPC, grpcSdkServer.getConnectionType());
        assertEquals(1001, grpcSdkServer.rpcPortOffset());
        grpcSdkServer.stopServer();
    }
}
