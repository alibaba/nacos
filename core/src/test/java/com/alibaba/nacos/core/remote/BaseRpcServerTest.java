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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BaseRpcServerTest {
    
    private MockedStatic<EnvUtil> envUtilMock;
    
    @AfterEach
    void tearDown() {
        if (envUtilMock != null) {
            envUtilMock.close();
        }
    }
    
    @Test
    void testGetServicePort() {
        envUtilMock = Mockito.mockStatic(EnvUtil.class);
        envUtilMock.when(EnvUtil::getPort).thenReturn(8848);
        BaseRpcServer server = new BaseRpcServer() {
            
            @Override
            public ConnectionType getConnectionType() {
                return ConnectionType.GRPC;
            }
            
            @Override
            public void reloadProtocolContext() {
            }
            
            @Override
            public void startServer() {
            }
            
            @Override
            public int rpcPortOffset() {
                return 1000;
            }
            
            @Override
            public void shutdownServer() {
            }
        };
        assertEquals(9848, server.getServicePort());
    }
    
    @Test
    void testStopServerDelegatesToShutdownServer() throws Exception {
        final boolean[] shutdownCalled = {false};
        BaseRpcServer server = new BaseRpcServer() {
            
            @Override
            public ConnectionType getConnectionType() {
                return ConnectionType.GRPC;
            }
            
            @Override
            public void reloadProtocolContext() {
            }
            
            @Override
            public void startServer() {
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public void shutdownServer() {
                shutdownCalled[0] = true;
            }
        };
        server.stopServer();
        assertTrue(shutdownCalled[0]);
    }
}
