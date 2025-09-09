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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.McpServerImportService;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpInnerHandlerTest {
    
    @Mock
    McpServerOperationService mcpServerOperationService;
    
    @Mock
    McpServerImportService mcpServerImportService;
    
    McpInnerHandler mcpInnerHandler;
    
    @BeforeEach
    void setUp() {
        mcpInnerHandler = new McpInnerHandler(mcpServerOperationService, mcpServerImportService);
    }
    
    @Test
    void listMcpServers() throws NacosException {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        when(mcpServerOperationService.listMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test",
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100)).thenReturn(mockPage);
        Page<McpServerBasicInfo> actual = mcpInnerHandler.listMcpServers(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test",
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getMcpServer() throws NacosException {
        McpServerDetailInfo mock = new McpServerDetailInfo();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test", "name",
                "version")).thenReturn(mock);
        McpServerDetailInfo actual = mcpInnerHandler.getMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "name", "test",
                "version");
        assertEquals(mock, actual);
    }
    
    @Test
    void createMcpServer() throws NacosException {
        mcpInnerHandler.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, new McpServerBasicInfo(),
                new McpToolSpecification(), new McpEndpointSpec());
        verify(mcpServerOperationService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                any(McpServerBasicInfo.class), any(McpToolSpecification.class), any(McpEndpointSpec.class));
    }
    
    @Test
    void updateMcpServer() throws NacosException {
        mcpInnerHandler.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true, new McpServerBasicInfo(),
                new McpToolSpecification(), new McpEndpointSpec());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(true),
                any(McpServerBasicInfo.class), any(McpToolSpecification.class), any(McpEndpointSpec.class));
    }
    
    @Test
    void deleteMcpServer() throws NacosException {
        mcpInnerHandler.deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test", "id", "version");
        verify(mcpServerOperationService).deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test", "id",
                "version");
    }
}
