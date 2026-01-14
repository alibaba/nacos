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
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.ReleaseMcpServerResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseMcpServerRequestHandlerTest {
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private McpEndpointOperationService endpointOperationService;
    
    @Mock
    private McpServerIndex mcpServerIndex;
    
    @Mock
    private RequestMeta meta;
    
    ReleaseMcpServerRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new ReleaseMcpServerRequestHandler(mcpServerOperationService, endpointOperationService,
                mcpServerIndex);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithInvalidParameter() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        ReleaseMcpServerResponse response = requestHandler.handle(request, null);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        request.setServerSpecification(serverSpecification);
        response = requestHandler.handle(request, null);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter 'serverSpecification.name' type String is not present");
        serverSpecification.setName("test");
        response = requestHandler.handle(request, null);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `serverSpecification.versionDetail.version` not present");
        ServerVersionDetail serverVersionDetail = new ServerVersionDetail();
        serverSpecification.setVersionDetail(serverVersionDetail);
        response = requestHandler.handle(request, null);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `serverSpecification.versionDetail.version` not present");
    }
    
    @Test
    void handleReleaseExistedServerAndVersion() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, false));
        McpServerDetailInfo detailInfo = buildMockServerDetail();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenReturn(detailInfo);
        when(meta.getConnectionId()).thenReturn("111");
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.CONFLICT,
                "Mcp Server test and target version 1.0.0 already exist, do not do release");
    }
    
    @Test
    void handleReleaseNewServerForSse() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, false));
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND, ""));
        when(endpointOperationService.generateService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test::1.0.0")).thenReturn(
                Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP, "test"));
        when(mcpServerOperationService.createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(request.getServerSpecification()), isNull(), isNotNull())).thenReturn(id);
        when(meta.getConnectionId()).thenReturn("111");
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertEquals(id, response.getMcpId());
    }
    
    @Test
    void handleReleaseNewServerForSseWithSpecifiedEndpoint() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, false));
        request.setEndpointSpecification(new McpEndpointSpec());
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND, ""));
        when(mcpServerOperationService.createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(request.getServerSpecification()), isNull(), isNotNull())).thenReturn(id);
        when(meta.getConnectionId()).thenReturn("111");
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertEquals(id, response.getMcpId());
        verify(endpointOperationService, never()).generateService(anyString(), anyString());
    }
    
    @Test
    void handleReleaseNewServerForStdio() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(true, false));
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND, ""));
        when(mcpServerOperationService.createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(request.getServerSpecification()), isNull(), isNull())).thenReturn(id);
        when(meta.getConnectionId()).thenReturn("111");
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertEquals(id, response.getMcpId());
    }
    
    @Test
    void handleReleaseNewVersionWithoutLatest() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, false));
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SEVER_VERSION_NOT_FOUND, ""));
        when(meta.getConnectionId()).thenReturn("111");
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        when(endpointOperationService.generateService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test::1.0.0")).thenReturn(
                Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP, "test"));
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertEquals(id, response.getMcpId());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(false),
                eq(request.getServerSpecification()), isNull(), isNotNull(), eq(false));
    }
    
    @Test
    void handleReleaseNewVersionWithoutLatestWithSpecifiedEndpoint() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, false));
        request.setEndpointSpecification(new McpEndpointSpec());
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SEVER_VERSION_NOT_FOUND, ""));
        when(meta.getConnectionId()).thenReturn("111");
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertEquals(id, response.getMcpId());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(false),
                eq(request.getServerSpecification()), isNull(), isNotNull(), eq(false));
        verify(endpointOperationService, never()).generateService(anyString(), anyString());
    }
    
    @Test
    void handleReleaseNewVersionWithLatest() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, true));
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SEVER_VERSION_NOT_FOUND, ""));
        when(meta.getConnectionId()).thenReturn("111");
        McpServerIndexData indexData = McpServerIndexData.newIndexData(id, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test")).thenReturn(indexData);
        when(endpointOperationService.generateService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test::1.0.0")).thenReturn(
                Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP, "test"));
        ReleaseMcpServerResponse response = requestHandler.handle(request, meta);
        assertEquals(id, response.getMcpId());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(true),
                eq(request.getServerSpecification()), isNull(), isNotNull(), eq(false));
    }
    
    @Test
    void handleReleaseWithException() throws NacosException {
        NacosApiException exceptedException = new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "test");
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setServerSpecification(buildMockServerSpecification(false, true));
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "test",
                "1.0.0")).thenThrow(exceptedException);
        try {
            requestHandler.handle(request, meta);
        } catch (NacosApiException e) {
            assertEquals(exceptedException, e);
        }
    }
    
    private McpServerBasicInfo buildMockServerSpecification(boolean isStdio, boolean isLatest) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName("test");
        ServerVersionDetail serverVersionDetail = new ServerVersionDetail();
        serverVersionDetail.setVersion("1.0.0");
        if (isLatest) {
            serverVersionDetail.setIs_latest(true);
        }
        result.setVersionDetail(serverVersionDetail);
        if (!isStdio) {
            result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
            result.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        } else {
            result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
            result.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        }
        return result;
    }
    
    private McpServerDetailInfo buildMockServerDetail() {
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setName("test");
        ServerVersionDetail serverVersionDetail = new ServerVersionDetail();
        serverVersionDetail.setVersion("1.0.0");
        result.setVersionDetail(serverVersionDetail);
        result.setId(UUID.randomUUID().toString());
        return result;
    }
    
    private void assertErrorResponse(ReleaseMcpServerResponse response, int code, String message) {
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(code, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
}