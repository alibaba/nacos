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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class McpProxyTest {
    
    private static final String NAMESPACE_ID = "testNamespace";
    
    private static final String MCP_NAME = "testMcp";
    
    @Mock
    private McpHandler mcpHandler;
    
    private McpProxy mcpProxy;
    
    @BeforeEach
    public void setUp() {
        mcpProxy = new McpProxy(mcpHandler);
    }
    
    @Test
    public void getMcpServer() throws NacosException {
        McpServerDetailInfo expectedInfo = new McpServerDetailInfo();
        when(mcpHandler.getMcpServer(NAMESPACE_ID, MCP_NAME, "id", "version")).thenReturn(expectedInfo);
        
        McpServerDetailInfo result = mcpProxy.getMcpServer(NAMESPACE_ID, MCP_NAME, "id", "version");
        
        assertNotNull(result);
        assertEquals(expectedInfo, result);
        verify(mcpHandler, times(1)).getMcpServer(NAMESPACE_ID, MCP_NAME, "id", "version");
    }
    
    @Test
    public void listMcpServers() throws NacosException {
        List<McpServerBasicInfo> serverList = new ArrayList<>();
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName("Test Server");
        serverList.add(server);
        
        final String search = "blur";
        final int pageNo = 1;
        final int pageSize = 10;
        Page<McpServerBasicInfo> expectedPage = new Page<>();
        expectedPage.setPageItems(serverList);
        expectedPage.setPageNumber(pageNo);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(serverList.size());
        
        when(mcpHandler.listMcpServers(NAMESPACE_ID, MCP_NAME, search, pageNo, pageSize)).thenReturn(expectedPage);
        
        Page<McpServerBasicInfo> result = mcpProxy.listMcpServers(NAMESPACE_ID, MCP_NAME, search, pageNo, pageSize);
        
        assertEquals(expectedPage, result);
        verify(mcpHandler, times(1)).listMcpServers(NAMESPACE_ID, MCP_NAME, search, pageNo, pageSize);
    }
    
    @Test
    public void createMcpServer() throws NacosException {
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        String mcpId = UUID.randomUUID().toString();
        serverSpecification.setId(mcpId);
        McpToolSpecification toolSpecification = new McpToolSpecification();
        McpEndpointSpec endpointSpecification = new McpEndpointSpec();
        
        when(mcpHandler.createMcpServer(NAMESPACE_ID, serverSpecification, toolSpecification, endpointSpecification)).thenReturn(mcpId);
        
        assertDoesNotThrow(() -> {
            mcpProxy.createMcpServer(NAMESPACE_ID, serverSpecification, toolSpecification,
                    endpointSpecification);
        });
        
        verify(mcpHandler, times(1)).createMcpServer(NAMESPACE_ID, serverSpecification, toolSpecification,
                endpointSpecification);
    }
    
    @Test
    public void updateMcpServer() throws NacosException {
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        McpToolSpecification toolSpecification = new McpToolSpecification();
        McpEndpointSpec endpointSpecification = new McpEndpointSpec();
        
        doNothing().when(mcpHandler)
                .updateMcpServer(NAMESPACE_ID, true, serverSpecification, toolSpecification, endpointSpecification, false);
        
        mcpProxy.updateMcpServer(NAMESPACE_ID, true, serverSpecification, toolSpecification, endpointSpecification, false);
    }

    @Test
    public void updateMcpServerWithOverrideExisting() throws NacosException {
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        McpToolSpecification toolSpecification = new McpToolSpecification();
        McpEndpointSpec endpointSpecification = new McpEndpointSpec();

        doNothing().when(mcpHandler)
                .updateMcpServer(NAMESPACE_ID, true, serverSpecification, toolSpecification, endpointSpecification, true);

        mcpProxy.updateMcpServer(NAMESPACE_ID, true, serverSpecification, toolSpecification, endpointSpecification, true);
    }
    
    @Test
    public void deleteMcpServer() throws NacosException {
        doNothing().when(mcpHandler).deleteMcpServer(NAMESPACE_ID, MCP_NAME, "id", "version");
        mcpProxy.deleteMcpServer(NAMESPACE_ID, MCP_NAME, "id", "version");
        verify(mcpHandler).deleteMcpServer(NAMESPACE_ID, MCP_NAME, "id", "version");
    }
}
