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

import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryMcpServerRequestHandlerTest {
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private McpServerIndex mcpServerIndex;
    
    QueryMcpServerRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new QueryMcpServerRequestHandler(mcpServerOperationService, mcpServerIndex);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithInvalidParam() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        QueryMcpServerResponse response = requestHandler.handle(request, null);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertEquals("parameters `mcpName` can't be empty or null", response.getMessage());
    }
    
    @Test
    void handleMcpServerNotFound() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setMcpName("test");
        QueryMcpServerResponse response = requestHandler.handle(request, null);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.NOT_FOUND, response.getErrorCode());
        assertEquals("MCP server `test` not found in namespaceId: `public`", response.getMessage());
    }
    
    @Test
    void handle() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setMcpName("test");
        McpServerIndexData indexData = new McpServerIndexData();
        indexData.setId(UUID.randomUUID().toString());
        indexData.setNamespaceId("public");
        when(mcpServerIndex.getMcpServerByName("public", "test")).thenReturn(indexData);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        when(mcpServerOperationService.getMcpServerDetail("public", indexData.getId(), null, null)).thenReturn(
                mcpServerDetailInfo);
        QueryMcpServerResponse response = requestHandler.handle(request, null);
        assertEquals(mcpServerDetailInfo, response.getMcpServerDetailInfo());
    }
}