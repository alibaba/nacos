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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.ai.remote.response.ReleaseMcpServerResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.remote.redo.AiGrpcRedoService;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGrpcClientTest {
    
    @Mock
    private RpcClient rpcClient;
    
    @Mock
    private AbstractServerListManager serverListManager;
    
    @Mock
    private AiGrpcRedoService redoService;
    
    @Mock
    private SecurityProxy securityProxy;
    
    @Mock
    private NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    @Mock
    private NacosAgentCardCacheHolder agentCardCacheHolder;
    
    AiGrpcClient aiGrpcClient;
    
    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        aiGrpcClient = new AiGrpcClient("test", clientProperties);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        aiGrpcClient.shutdown();
    }
    
    @Test
    void start() throws NacosException {
        assertDoesNotThrow(() -> aiGrpcClient.start(mcpServerCacheHolder, agentCardCacheHolder));
    }
    
    @Test
    void queryMcpServer() throws NacosException, NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        QueryMcpServerResponse response = new QueryMcpServerResponse();
        response.setMcpServerDetailInfo(mcpServerDetailInfo);
        when(rpcClient.request(any(QueryMcpServerRequest.class))).thenReturn(response);
        McpServerDetailInfo actual = aiGrpcClient.queryMcpServer("test", "1.0.0");
        assertEquals(mcpServerDetailInfo, actual);
    }
    
    @Test
    void queryMcpServerWithErrorCode() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        Response response = ErrorResponse.build(NacosException.INVALID_PARAM, "test");
        when(rpcClient.request(any(QueryMcpServerRequest.class))).thenReturn(response);
        assertThrows(NacosException.class, () -> aiGrpcClient.queryMcpServer("test", "1.0.0"));
    }
    
    @Test
    void queryMcpServerWithNoRight() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        Response response = ErrorResponse.build(NacosException.NO_RIGHT, "test");
        when(rpcClient.request(any(QueryMcpServerRequest.class))).thenReturn(response);
        assertThrows(NacosException.class, () -> aiGrpcClient.queryMcpServer("test", "1.0.0"));
        verify(securityProxy).reLogin();
    }
    
    @Test
    void queryMcpServerWithUnExpectedResponse() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        ReleaseMcpServerResponse response = new ReleaseMcpServerResponse();
        when(rpcClient.request(any(QueryMcpServerRequest.class))).thenReturn(response);
        assertThrows(NacosException.class, () -> aiGrpcClient.queryMcpServer("test", "1.0.0"));
    }
    
    @Test
    void queryMcpServerWithException() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        when(rpcClient.request(any(QueryMcpServerRequest.class))).thenThrow(new RuntimeException("test"));
        assertThrows(NacosException.class, () -> aiGrpcClient.queryMcpServer("test", "1.0.0"));
    }
    
    @Test
    void releaseMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setVersionDetail(new ServerVersionDetail());
        serverSpec.getVersionDetail().setVersion("1.0.0");
        String id = UUID.randomUUID().toString();
        ReleaseMcpServerResponse response = new ReleaseMcpServerResponse();
        response.setMcpId(id);
        when(rpcClient.request(any(ReleaseMcpServerRequest.class))).thenReturn(response);
        assertEquals(id, aiGrpcClient.releaseMcpServer(serverSpec, new McpToolSpecification(), null));
    }
    
    @Test
    void registerMcpServerEndpoint() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        McpServerEndpointResponse response = new McpServerEndpointResponse();
        response.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        when(rpcClient.request(any(McpServerEndpointRequest.class))).thenReturn(response);
        aiGrpcClient.registerMcpServerEndpoint("test", "127.0.0.1", 8080, "1.0.0");
        verify(redoService).cachedMcpServerEndpointForRedo("test", "127.0.0.1", 8080, "1.0.0");
        verify(redoService).mcpServerEndpointRegistered("test");
    }
    
    @Test
    void deregisterMcpServerEndpoint() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        McpServerEndpointResponse response = new McpServerEndpointResponse();
        response.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        when(rpcClient.request(any(McpServerEndpointRequest.class))).thenReturn(response);
        aiGrpcClient.deregisterMcpServerEndpoint("test", "127.0.0.1", 8080);
        verify(redoService).mcpServerEndpointDeregister("test");
        verify(redoService).mcpServerEndpointDeregistered("test");
    }
    
    @Test
    void subscribeMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        QueryMcpServerResponse response = new QueryMcpServerResponse();
        response.setMcpServerDetailInfo(mcpServerDetailInfo);
        when(rpcClient.request(any(QueryMcpServerRequest.class))).thenReturn(response);
        assertEquals(mcpServerDetailInfo, aiGrpcClient.subscribeMcpServer("test", null));
        verify(mcpServerCacheHolder).processMcpServerDetailInfo(mcpServerDetailInfo);
        verify(mcpServerCacheHolder).addMcpServerUpdateTask("test", null);
    }
    
    @Test
    void subscribeMcpServerAlreadySubscribed() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        when(mcpServerCacheHolder.getMcpServer("test", null)).thenReturn(mcpServerDetailInfo);
        assertEquals(mcpServerDetailInfo, aiGrpcClient.subscribeMcpServer("test", null));
        verify(rpcClient, never()).request(any(QueryMcpServerRequest.class));
        verify(mcpServerCacheHolder, never()).processMcpServerDetailInfo(mcpServerDetailInfo);
        verify(mcpServerCacheHolder, never()).addMcpServerUpdateTask("test", null);
    }
    
    @Test
    void unsubscribeMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.SUPPORTED);
        aiGrpcClient.unsubscribeMcpServer("test", null);
        verify(mcpServerCacheHolder).removeMcpServerUpdateTask("test", null);
    }
    
    @Test
    void queryMcpServerWithFeatureDisabled() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class, () -> aiGrpcClient.queryMcpServer("test", "1.0.0"));
    }
    
    @Test
    void releaseMcpServerWithFeatureDisabled() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setVersionDetail(new ServerVersionDetail());
        serverSpec.getVersionDetail().setVersion("1.0.0");
        assertThrows(NacosRuntimeException.class, () -> aiGrpcClient.releaseMcpServer(serverSpec, null, null));
    }
    
    @Test
    void registerMcpServerEndpointWithFeatureDisabled() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class,
                () -> aiGrpcClient.registerMcpServerEndpoint("test", "127.0.0.1", 8080, "1.0.0"));
    }
    
    @Test
    void deregisterMcpServerEndpointWithFeatureDisabled() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class,
                () -> aiGrpcClient.deregisterMcpServerEndpoint("test", "127.0.0.1", 8080));
    }
    
    @Test
    void subscribeMcpServerWithFeatureDisabled() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class, () -> aiGrpcClient.subscribeMcpServer("test", null));
    }
    
    @Test
    void unsubscribeMcpServerWithFeatureDisabled() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class, () -> aiGrpcClient.unsubscribeMcpServer("test", null));
    }
    
    @Test
    void isEnable() throws NoSuchFieldException, IllegalAccessException {
        injectMock();
        assertFalse(aiGrpcClient.isEnable());
        when(rpcClient.isRunning()).thenReturn(true);
        assertTrue(aiGrpcClient.isEnable());
    }
    
    @Test
    void requestToServerWithoutMcpRequest() throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        Method method = AiGrpcClient.class.getDeclaredMethod("requestToServer", Request.class, Class.class);
        method.setAccessible(true);
        injectMock();
        try {
            method.invoke(aiGrpcClient, new InstanceRequest(), InstanceResponse.class);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            assertInstanceOf(NacosException.class, targetException);
            assertEquals(400, ((NacosException) targetException).getErrCode());
        }
    }
    
    private void injectMock() throws NoSuchFieldException, IllegalAccessException {
        Field field = AiGrpcClient.class.getDeclaredField("rpcClient");
        field.setAccessible(true);
        RpcClient autoRpcClient = (RpcClient) field.get(aiGrpcClient);
        field.set(aiGrpcClient, rpcClient);
        
        field = AiGrpcClient.class.getDeclaredField("serverListManager");
        field.setAccessible(true);
        AbstractServerListManager autoServerListManager = (AbstractServerListManager) field.get(aiGrpcClient);
        field.set(aiGrpcClient, serverListManager);
        
        field = AiGrpcClient.class.getDeclaredField("redoService");
        field.setAccessible(true);
        AiGrpcRedoService autoRedoService = (AiGrpcRedoService) field.get(aiGrpcClient);
        field.set(aiGrpcClient, redoService);
        
        field = AiGrpcClient.class.getDeclaredField("securityProxy");
        field.setAccessible(true);
        field.set(aiGrpcClient, securityProxy);
        field = AiGrpcClient.class.getDeclaredField("mcpServerCacheHolder");
        field.setAccessible(true);
        field.set(aiGrpcClient, mcpServerCacheHolder);
        
        try {
            autoRpcClient.shutdown();
            autoServerListManager.shutdown();
            autoRedoService.shutdown();
        } catch (NacosException ignored) {
        }
    }
}