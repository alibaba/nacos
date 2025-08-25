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

package com.alibaba.nacos.ai.remote.handler;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerEndpointRequestHandlerTest {
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private McpServerIndex mcpServerIndex;
    
    @Mock
    private RequestMeta meta;
    
    McpServerEndpointRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new McpServerEndpointRequestHandler(clientOperationService, mcpServerOperationService,
                mcpServerIndex);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithInvalidParameters() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM, "parameters `mcpName` can't be empty or null");
    }
    
    @Test
    void handleForNotFound() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setMcpName("test");
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.NOT_FOUND, "MCP server `test` not found in namespaceId: `public`");
    }
    
    @Test
    void handleForRegisterEndpoint() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setMcpName("test");
        request.setVersion("1.0.0");
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        String id = UUID.randomUUID().toString();
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                "1.0.0")).thenReturn(buildMockMcpServerDetailInfo());
        when(meta.getConnectionId()).thenReturn("TEST_CONNECTION_ID");
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT, response.getType());
        verify(clientOperationService).registerInstance(any(Service.class), any(Instance.class),
                eq("TEST_CONNECTION_ID"));
    }
    
    @Test
    void handleForDeregisterEndpoint() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setMcpName("test");
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        String id = UUID.randomUUID().toString();
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                null)).thenReturn(buildMockMcpServerDetailInfo());
        when(meta.getConnectionId()).thenReturn("TEST_CONNECTION_ID");
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertEquals(AiRemoteConstants.DE_REGISTER_ENDPOINT, response.getType());
        verify(clientOperationService).deregisterInstance(any(Service.class), any(Instance.class),
                eq("TEST_CONNECTION_ID"));
    }
    
    @Test
    void handleForInvalidType() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setMcpName("test");
        request.setType("INVALID_TYPE");
        String id = UUID.randomUUID().toString();
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                null)).thenReturn(buildMockMcpServerDetailInfo());
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "parameter `type` should be registerEndpoint or deregisterEndpoint, but was INVALID_TYPE");
    }
    
    @Test
    void handleForRegisterFrontendEndpoint() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setMcpName("test");
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        String id = UUID.randomUUID().toString();
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        McpServerDetailInfo detailInfo = buildMockMcpServerDetailInfo();
        detailInfo.getRemoteServerConfig()
                .setFrontEndpointConfigList(Collections.singletonList(new FrontEndpointConfig()));
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setEndpointType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setType(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setEndpointData(detailInfo.getRemoteServerConfig().getServiceRef());
        detailInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                null)).thenReturn(detailInfo);
        when(meta.getConnectionId()).thenReturn("TEST_CONNECTION_ID");
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT, response.getType());
        verify(clientOperationService).registerInstance(any(Service.class), any(Instance.class),
                eq("TEST_CONNECTION_ID"));
    }
    
    @Test
    void handleForRegisterFrontendEndpointNotFound() throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setMcpName("test");
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        String id = UUID.randomUUID().toString();
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        McpServerDetailInfo detailInfo = buildMockMcpServerDetailInfo();
        detailInfo.getRemoteServerConfig()
                .setFrontEndpointConfigList(Collections.singletonList(new FrontEndpointConfig()));
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setEndpointType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setType(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        detailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0).setEndpointData("127.0.0.1:8848");
        detailInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                null)).thenReturn(detailInfo);
        McpServerEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.NOT_FOUND, "The Mcp Server Ref endpoint service not found.");
    }
    
    McpServerDetailInfo buildMockMcpServerDetailInfo() {
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setName("test");
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        serviceRef.setGroupName(Constants.MCP_SERVER_ENDPOINT_GROUP);
        serviceRef.setServiceName("test");
        McpServerRemoteServiceConfig remoteServiceConfig = new McpServerRemoteServiceConfig();
        remoteServiceConfig.setServiceRef(serviceRef);
        result.setRemoteServerConfig(remoteServiceConfig);
        return result;
    }
    
    private void assertErrorResponse(McpServerEndpointResponse response, int code, String message) {
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(code, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
}