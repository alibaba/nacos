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

package com.alibaba.nacos.test.ability;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.ability.discover.NacosAbilityManagerHolder;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClient;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestFilters;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.test.ability.component.TestServerAbilityControlManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@SuppressWarnings("all")
class AbilityDiscovery {
    
    @LocalServerPort
    private int port;
    
    @Resource
    private RequestHandlerRegistry requestHandlerRegistry;
    
    @Resource
    private RequestFilters filters;
    
    @Resource
    private ConnectionManager connectionManager;
    
    private RpcClient client;
    
    private RpcClient clusterClient;
    
    private ConfigService configService;
    
    private AbstractAbilityControlManager oldInstance;
    
    /**
     * test server judge client abilities
     */
    private volatile boolean serverSuccess = false;
    
    private volatile boolean clientSuccess = false;
    
    private volatile boolean clusterSuccess = false;
    
    private Field abstractAbilityControlManager;
    
    private Field registryHandlerFields;
    
    private Field serverReuqestHandlersField;
    
    private Field currentConnField;
    
    private Field setupRequestHandlerField;
    
    @BeforeEach
    void setup() throws NoSuchFieldException, IllegalAccessException, NacosException {
        // load class
        oldInstance = NacosAbilityManagerHolder.getInstance();
        
        // replace
        abstractAbilityControlManager = NacosAbilityManagerHolder.class.getDeclaredField("abstractAbilityControlManager");
        abstractAbilityControlManager.setAccessible(true);
        abstractAbilityControlManager.set(NacosAbilityManagerHolder.class, new TestServerAbilityControlManager());
        
        // get registry field
        registryHandlerFields = RequestHandlerRegistry.class.getDeclaredField("registryHandlers");
        registryHandlerFields.setAccessible(true);
        
        // currentConn
        currentConnField = RpcClient.class.getDeclaredField("currentConnection");
        currentConnField.setAccessible(true);
        
        // init config service
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + port);
        configService = NacosFactory.createConfigService(properties);
        
        // server request handler
        serverReuqestHandlersField = RpcClient.class.getDeclaredField("serverRequestHandlers");
        serverReuqestHandlersField.setAccessible(true);
        
        // setupRequestHandler
        setupRequestHandlerField = GrpcClient.class.getDeclaredField("setupRequestHandler");
        setupRequestHandlerField.setAccessible(true);
        
        // init client
        client = RpcClientFactory.createClient(UUID.randomUUID().toString(), ConnectionType.GRPC, new HashMap<>());
        client.serverListFactory(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "127.0.0.1:" + port;
            }
            
            @Override
            public String getCurrentServer() {
                return "127.0.0.1:" + port;
            }
            
            @Override
            public List<String> getServerList() {
                return Collections.singletonList("127.0.0.1:" + port);
            }
        });
        // connect to server
        client.start();
        
        clusterClient = RpcClientFactory.createClusterClient(UUID.randomUUID().toString(), ConnectionType.GRPC, new HashMap<>());
        clusterClient.serverListFactory(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "127.0.0.1:" + port;
            }
            
            @Override
            public String getCurrentServer() {
                return "127.0.0.1:" + port;
            }
            
            @Override
            public List<String> getServerList() {
                return Collections.singletonList("127.0.0.1:" + port);
            }
        });
        // connect to server
        clusterClient.start();
    }
    
    @Test
    void testClientDiscovery() throws NacosException {
        // client judge ability
        assertEquals(AbilityStatus.SUPPORTED, client.getConnectionAbility(AbilityKey.SERVER_TEST_1));
        assertEquals(AbilityStatus.NOT_SUPPORTED, client.getConnectionAbility(AbilityKey.SERVER_TEST_2));
    }
    
    @Test
    void testServerDiscoveryAndJudge() throws Exception {
        Map<String, RequestHandler> handlers = (Map<String, RequestHandler>) registryHandlerFields.get(requestHandlerRegistry);
        
        // set handler
        RequestHandler oldRequestHandler = handlers.remove(ConfigQueryRequest.class.getSimpleName());
        handlers.put(ConfigQueryRequest.class.getSimpleName(), new ClientRequestHandler(filters));
        configService.getConfig("test", "DEFAULT_GROUP", 2000);
        // wait server invoke
        Thread.sleep(3000);
        assertTrue(serverSuccess);
        // recover
        handlers.remove(ConfigQueryRequest.class.getSimpleName());
        handlers.put(ConfigQueryRequest.class.getSimpleName(), oldRequestHandler);
    }
    
    @Test
    void testClientJudge() throws Exception {
        List<ServerRequestHandler> handlers = (List<ServerRequestHandler>) serverReuqestHandlersField.get(client);
        handlers.clear();
        // register
        client.registerServerRequestHandler(new ServerRequestHandler() {
            @Override
            public Response requestReply(Request request, Connection connection) {
                if (connection.getConnectionAbility(AbilityKey.SERVER_TEST_1).equals(AbilityStatus.SUPPORTED)
                        && connection.getConnectionAbility(AbilityKey.SERVER_TEST_2).equals(AbilityStatus.NOT_SUPPORTED)) {
                    clientSuccess = true;
                }
                return new Response() {
                };
            }
        });
        
        // get id
        Connection conn = (Connection) currentConnField.get(client);
        
        com.alibaba.nacos.core.remote.Connection connection = connectionManager.getConnection(conn.getConnectionId());
        try {
            connection.request(new NotifySubscriberRequest(), 2000L);
        } catch (NacosException e) {
            // nothing to do
        }
        
        // wait client react
        Thread.sleep(4000);
        assertTrue(clientSuccess);
    }
    
    @Test
    void testClusterClient() throws IllegalAccessException, NacosException, InterruptedException, NoSuchFieldException {
        Map<String, RequestHandler> handlers = (Map<String, RequestHandler>) registryHandlerFields.get(requestHandlerRegistry);
        
        // set handler
        RequestHandler oldRequestHandler = handlers.remove(ConfigQueryRequest.class.getSimpleName());
        handlers.put(ConfigQueryRequest.class.getSimpleName(), new ClusterClientRequestHandler(filters));
        configService.getConfig("test", "DEFAULT_GROUP", 2000);
        // wait server invoke
        Thread.sleep(3000);
        assertTrue(clusterSuccess);
        // recover
        handlers.remove(ConfigQueryRequest.class.getSimpleName());
        handlers.put(ConfigQueryRequest.class.getSimpleName(), oldRequestHandler);
    }
    
    @Test
    void testNegotiationTimeout() throws Exception {
        Object origin = setupRequestHandlerField.get(client);
        // set null for setupRequestHandlerField
        setupRequestHandlerField.set(client, null);
        // try connect
        Connection connection = client.connectToServer(new RpcClient.ServerInfo("127.0.0.1", port));
        assertNull(connection);
        // recovery
        setupRequestHandlerField.set(client, origin);
    }
    
    @AfterEach
    void recover() throws IllegalAccessException, NacosException {
        abstractAbilityControlManager.set(NacosAbilityManagerHolder.class, oldInstance);
        client.shutdown();
    }
    
    /**
     * just to test ability
     */
    class ClientRequestHandler extends RequestHandler<ConfigQueryRequest, ConfigQueryResponse> {
        
        public ClientRequestHandler(RequestFilters requestFilters) throws NoSuchFieldException, IllegalAccessException {
            Field declaredField = RequestHandler.class.getDeclaredField("requestFilters");
            declaredField.setAccessible(true);
            declaredField.set(this, requestFilters);
        }
        
        @Override
        public ConfigQueryResponse handle(ConfigQueryRequest request, RequestMeta meta) throws NacosException {
            if (meta.getConnectionAbility(AbilityKey.SDK_CLIENT_TEST_1).equals(AbilityStatus.SUPPORTED)) {
                serverSuccess = true;
            }
            return new ConfigQueryResponse();
        }
    }
    
    /**
     * just to test ability.
     */
    class ClusterClientRequestHandler extends RequestHandler<ConfigQueryRequest, ConfigQueryResponse> {
        
        public ClusterClientRequestHandler(RequestFilters requestFilters) throws NoSuchFieldException, IllegalAccessException {
            Field declaredField = RequestHandler.class.getDeclaredField("requestFilters");
            declaredField.setAccessible(true);
            declaredField.set(this, requestFilters);
        }
        
        @Override
        public ConfigQueryResponse handle(ConfigQueryRequest request, RequestMeta meta) throws NacosException {
            if (meta.getConnectionAbility(AbilityKey.CLUSTER_CLIENT_TEST_1).equals(AbilityStatus.SUPPORTED)) {
                clusterSuccess = true;
            }
            return new ConfigQueryResponse();
        }
    }
}
