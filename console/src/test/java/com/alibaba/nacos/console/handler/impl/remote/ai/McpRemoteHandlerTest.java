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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    McpRemoteHandler mcpRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithAi();
        mcpRemoteHandler = new McpRemoteHandler(clientHolder);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listMcpServersForBlur() throws NacosException {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        when(mcpMaintainerService.searchMcpServer("", "", 1, 100)).thenReturn(mockPage);
        Page<McpServerBasicInfo> actual =
            mcpRemoteHandler.listMcpServers("", "", Constants.MCP_LIST_SEARCH_BLUR, 1,
                100);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void listMcpServersForAccurate() throws NacosException {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        when(mcpMaintainerService.listMcpServer("", "", 1, 100)).thenReturn(mockPage);
        Page<McpServerBasicInfo> actual =
            mcpRemoteHandler.listMcpServers("", "", Constants.MCP_LIST_SEARCH_ACCURATE, 1,
                100);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getMcpServer() throws NacosException {
        McpServerDetailInfo mock = new McpServerDetailInfo();
        when(mcpMaintainerService.getMcpServerDetail("", "test", "id", "version")).thenReturn(mock);
        McpServerDetailInfo actual = mcpRemoteHandler.getMcpServer("", "test", "id", "version");
        assertEquals(mock, actual);
    }
    
    @Test
    void createMcpServer() throws NacosException {
        McpServerBasicInfo mcpServerBasicInfo = new McpServerBasicInfo();
        mcpServerBasicInfo.setName("test");
        mcpRemoteHandler.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mcpServerBasicInfo,
            new McpToolSpecification(), new McpEndpointSpec());
        verify(mcpMaintainerService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq("test"),
            any(McpServerBasicInfo.class), any(McpToolSpecification.class),
            any(McpEndpointSpec.class));
    }
    
    @Test
    void updateMcpServer() throws NacosException {
        McpServerBasicInfo mcpServerBasicInfo = new McpServerBasicInfo();
        mcpServerBasicInfo.setName("test");
        mcpRemoteHandler.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
            mcpServerBasicInfo,
            new McpToolSpecification(), new McpEndpointSpec(), false);
        verify(mcpMaintainerService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq("test"), eq(true),
            any(McpServerBasicInfo.class), any(McpToolSpecification.class),
            any(McpEndpointSpec.class), eq(false));
    }
    
    @Test
    void updateMcpServerWithOverrideExisting() throws NacosException {
        McpServerBasicInfo mcpServerBasicInfo = new McpServerBasicInfo();
        mcpServerBasicInfo.setName("test");
        mcpRemoteHandler.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
            mcpServerBasicInfo,
            new McpToolSpecification(), new McpEndpointSpec(), true);
        verify(mcpMaintainerService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq("test"), eq(true),
            any(McpServerBasicInfo.class), any(McpToolSpecification.class),
            any(McpEndpointSpec.class), eq(true));
    }
    
    @Test
    void deleteMcpServer() throws NacosException {
        mcpRemoteHandler.deleteMcpServer("", "test", "id", "version");
        verify(mcpMaintainerService).deleteMcpServer("", "test", "id", "version");
    }
    
    @Test
    void validateImportThrows() {
        assertThrows(NacosApiException.class,
            () -> mcpRemoteHandler.validateImport("ns", new McpServerImportRequest()));
    }
    
    @Test
    void executeImportThrows() {
        assertThrows(NacosApiException.class,
            () -> mcpRemoteHandler.executeImport("ns", new McpServerImportRequest()));
    }
}
