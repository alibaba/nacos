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

import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryMcpServerRequestHandlerTest {
    
    @Mock
    private McpOperationService mcpServerOperationService;
    
    QueryMcpServerRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new QueryMcpServerRequestHandler(mcpServerOperationService);
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
    void handleMcpServerNotFoundWhenLifecycleLookupThrows() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setMcpName("test");
        when(mcpServerOperationService.getMcpServerDetail("public", null, "test", null))
            .thenThrow(new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Resource not found"));
        QueryMcpServerResponse response = requestHandler.handle(request, null);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.NOT_FOUND, response.getErrorCode());
        assertEquals("MCP server `test` not found in namespaceId: `public`", response.getMessage());
    }
    
    @Test
    void handleMcpServerLookupFailure() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setMcpName("test");
        NacosException expected = new NacosException(NacosException.SERVER_ERROR, "failed");
        when(mcpServerOperationService.getMcpServerDetail("public", null, "test", null))
            .thenThrow(expected);
        NacosException actual = assertThrows(NacosException.class,
            () -> requestHandler.handle(request, null));
        assertSame(expected, actual);
    }
    
    @Test
    void handle() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setMcpName("test");
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        when(mcpServerOperationService.getMcpServerDetail("public", null, "test", null))
            .thenReturn(
                mcpServerDetailInfo);
        QueryMcpServerResponse response = requestHandler.handle(request, null);
        assertEquals(mcpServerDetailInfo, response.getMcpServerDetailInfo());
    }
}
