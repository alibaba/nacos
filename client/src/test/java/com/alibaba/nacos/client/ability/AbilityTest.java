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
 */

package com.alibaba.nacos.client.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.remote.TestConnection;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientConfig;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbilityTest {
    
    private RpcClient rpcClient;
    
    private Connection connection;
    
    @Test
    void testReceive() throws Exception {
        rpcClient = new RpcClient(new RpcClientConfig() {
            @Override
            public String name() {
                return "test";
            }
            
            @Override
            public int retryTimes() {
                return 1;
            }
            
            @Override
            public long timeOutMills() {
                return 3000L;
            }
            
            @Override
            public long connectionKeepAlive() {
                return 5000L;
            }
            
            @Override
            public int healthCheckRetryTimes() {
                return 1;
            }
            
            @Override
            public long healthCheckTimeOut() {
                return 3000L;
            }
            
            @Override
            public Map<String, String> labels() {
                return new HashMap<>();
            }
        }) {
            
            @Override
            public ConnectionType getConnectionType() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) throws Exception {
                connection = new Connection(new RpcClient.ServerInfo()) {
                    
                    {
                        super.abilityTable = new HashMap<>();
                        super.abilityTable.put(AbilityKey.SERVER_TEST_1.getName(), true);
                        super.abilityTable.put(AbilityKey.SERVER_TEST_2.getName(), false);
                    }
                    
                    @Override
                    public Response request(Request request, long timeoutMills) throws NacosException {
                        return null;
                    }
                    
                    @Override
                    public RequestFuture requestFuture(Request request) throws NacosException {
                        return null;
                    }
                    
                    @Override
                    public void asyncRequest(Request request, RequestCallBack requestCallBack) throws NacosException {
                    
                    }
                    
                    @Override
                    public void close() {
                    
                    }
                };
                ;
                return connection;
            }
        };
        rpcClient.start();
        // test not ready
        assertNull(rpcClient.getConnectionAbility(AbilityKey.SERVER_TEST_1));
        
        // test ready
        rpcClient.serverListFactory(new ServerListFactory() {
            
            @Override
            public String genNextServer() {
                return "localhost:8848";
            }
            
            @Override
            public String getCurrentServer() {
                return "localhost:8848";
            }
            
            @Override
            public List<String> getServerList() {
                return null;
            }
        });
        rpcClient.start();
        // if connect successfully
        assertEquals(AbilityStatus.SUPPORTED, rpcClient.getConnectionAbility(AbilityKey.SERVER_TEST_1));
        assertEquals(AbilityStatus.NOT_SUPPORTED, rpcClient.getConnectionAbility(AbilityKey.SERVER_TEST_2));
    }
    
    @AfterEach
    void testServerRequestAbility() {
        //test support
        ServerRequestHandler serverRequestHandler = (request, connection) -> {
            assertEquals(AbilityStatus.SUPPORTED, connection.getConnectionAbility(AbilityKey.SERVER_TEST_1));
            assertEquals(AbilityStatus.NOT_SUPPORTED, connection.getConnectionAbility(AbilityKey.SERVER_TEST_2));
            return new Response() {
            };
        };
        serverRequestHandler.requestReply(null, connection);
        
        // test no ability table
        serverRequestHandler = (request, connection) -> {
            assertEquals(AbilityStatus.UNKNOWN, connection.getConnectionAbility(AbilityKey.SERVER_TEST_1));
            return new Response() {
            };
        };
        serverRequestHandler.requestReply(null, new TestConnection(new RpcClient.ServerInfo()));
    }
    
}
