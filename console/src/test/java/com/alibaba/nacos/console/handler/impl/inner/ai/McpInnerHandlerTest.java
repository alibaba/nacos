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
import com.alibaba.nacos.ai.service.McpLegacyImportAdapter;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityOperationService;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpInnerHandlerTest {
    
    @Mock
    McpOperationService mcpServerOperationService;
    
    @Mock
    McpLegacyImportAdapter mcpLegacyImportAdapter;
    
    @Mock
    McpCompatibilityOperationService lifecycleOperationService;
    
    McpInnerHandler mcpInnerHandler;
    
    @BeforeEach
    void setUp() {
        mcpInnerHandler = new McpInnerHandler(mcpServerOperationService, mcpLegacyImportAdapter,
            lifecycleOperationService);
    }
    
    @Test
    void listMcpServers() throws NacosException {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        when(mcpServerOperationService.listMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            "test",
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100)).thenReturn(mockPage);
        Page<McpServerBasicInfo> actual =
            mcpInnerHandler.listMcpServers(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test",
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getMcpServer() throws NacosException {
        McpServerDetailInfo mock = new McpServerDetailInfo();
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            "test", "name",
            "version")).thenReturn(mock);
        McpServerDetailInfo actual =
            mcpInnerHandler.getMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "name", "test",
                "version");
        assertEquals(mock, actual);
    }
    
    @Test
    void createMcpServer() throws NacosException {
        mcpInnerHandler.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            new McpServerBasicInfo(),
            new McpToolSpecification(), new McpEndpointSpec());
        verify(mcpServerOperationService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            any(McpServerBasicInfo.class), any(McpToolSpecification.class),
            any(McpEndpointSpec.class));
    }
    
    @Test
    void updateMcpServer() throws NacosException {
        mcpInnerHandler.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
            new McpServerBasicInfo(),
            new McpToolSpecification(), new McpEndpointSpec(), false);
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq(true),
            any(McpServerBasicInfo.class), any(McpToolSpecification.class),
            any(McpEndpointSpec.class), eq(false));
    }
    
    @Test
    void updateMcpServerWithOverrideExisting() throws NacosException {
        mcpInnerHandler.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
            new McpServerBasicInfo(),
            new McpToolSpecification(), new McpEndpointSpec(), true);
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq(true),
            any(McpServerBasicInfo.class), any(McpToolSpecification.class),
            any(McpEndpointSpec.class), eq(true));
    }
    
    @Test
    void deleteMcpServer() throws NacosException {
        mcpInnerHandler.deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "test", "id",
            "version");
        verify(mcpServerOperationService).deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            "test", "id",
            "version");
    }
    
    @Test
    void standardLifecycleMethodsDelegateToCompatibilityRouter() throws NacosException {
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        McpLifecycleVersionDetail detail = new McpLifecycleVersionDetail();
        McpLifecycleVersionSummary summary = new McpLifecycleVersionSummary();
        Page<McpLifecycleVersionSummary> page = new Page<>();
        Map<String, String> labels = Map.of("stable", "1.0.0");
        when(lifecycleOperationService.listLifecycleVersions("ns", "test", "draft", 1, 10))
            .thenReturn(page);
        when(lifecycleOperationService.getLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(detail);
        when(lifecycleOperationService.createLifecycleDraft("ns", server, tools, resources,
            endpoint)).thenReturn(detail);
        when(lifecycleOperationService.updateLifecycleDraft("ns", server, tools, resources,
            endpoint)).thenReturn(detail);
        when(lifecycleOperationService.submitLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.publishLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.forcePublishLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.redraftLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.onlineLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.offlineLifecycleVersion("ns", "test", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.updateLifecycleLabels("ns", "test", labels))
            .thenReturn(labels);
        
        assertEquals(page,
            mcpInnerHandler.listLifecycleVersions("ns", "test", "draft", 1, 10));
        assertEquals(detail,
            mcpInnerHandler.getLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(detail,
            mcpInnerHandler.createLifecycleDraft("ns", server, tools, resources, endpoint));
        assertEquals(detail,
            mcpInnerHandler.updateLifecycleDraft("ns", server, tools, resources, endpoint));
        mcpInnerHandler.deleteLifecycleDraft("ns", "test", "1.0.0");
        assertEquals(summary,
            mcpInnerHandler.submitLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(summary,
            mcpInnerHandler.publishLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(summary,
            mcpInnerHandler.forcePublishLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(summary,
            mcpInnerHandler.redraftLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(summary,
            mcpInnerHandler.onlineLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(summary,
            mcpInnerHandler.offlineLifecycleVersion("ns", "test", "1.0.0"));
        assertEquals(labels, mcpInnerHandler.updateLifecycleLabels("ns", "test", labels));
        verify(lifecycleOperationService).deleteLifecycleDraft("ns", "test", "1.0.0");
    }
    
    @Test
    void validateImport() throws NacosException {
        McpServerImportRequest request = new McpServerImportRequest();
        McpServerImportValidationResult expected = new McpServerImportValidationResult();
        when(mcpLegacyImportAdapter.validateImport(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, request))
            .thenReturn(expected);
        
        McpServerImportValidationResult result =
            mcpInnerHandler.validateImport(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, request);
        
        assertNotNull(result);
        assertEquals(expected, result);
    }
    
    @Test
    void executeImport() throws NacosException {
        McpServerImportRequest request = new McpServerImportRequest();
        McpServerImportResponse expected = new McpServerImportResponse();
        when(mcpLegacyImportAdapter.executeImport(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, request))
            .thenReturn(expected);
        
        McpServerImportResponse result =
            mcpInnerHandler.executeImport(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, request);
        
        assertNotNull(result);
        assertEquals(expected, result);
    }
}
