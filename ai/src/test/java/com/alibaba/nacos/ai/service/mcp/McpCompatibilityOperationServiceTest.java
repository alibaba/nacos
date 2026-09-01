/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpCompatibilityOperationServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_NAME = "weather";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Mock
    private McpCompatibilityModeResolver modeResolver;
    
    @Mock
    private LegacyMcpOperationService legacyService;
    
    @Mock
    private McpLifecycleOperationService lifecycleService;
    
    private McpCompatibilityOperationService service;
    
    @BeforeEach
    void setUp() {
        service = new McpCompatibilityOperationService(modeResolver, legacyService,
            lifecycleService);
        org.mockito.Mockito.lenient().when(
            modeResolver.localMemberSupportsManagedLifecycle()).thenReturn(true);
    }
    
    @Test
    void testRoutesCompleteContractToLegacyWhileSyncing() throws Exception {
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        Page<McpServerBasicInfo> page = new Page<>();
        McpServerDetailInfo detail = new McpServerDetailInfo();
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.SYNCING);
        when(legacyService.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME, "accurate", 1, 10))
            .thenReturn(page);
        when(legacyService.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, "1.0.0"))
            .thenReturn(detail);
        when(legacyService.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, "1.0.0"))
            .thenReturn(detail);
        when(legacyService.createMcpServer(NAMESPACE_ID, server, tools, resources, endpoint))
            .thenReturn(MCP_ID);
        
        assertEquals(page,
            service.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME, "accurate", 1, 10));
        assertEquals(detail,
            service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, "1.0.0"));
        assertEquals(detail,
            service.getServingMcpServerDetail(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, server, tools, resources, endpoint));
        service.updateMcpServer(NAMESPACE_ID, true, server, tools, resources, endpoint, false);
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, "1.0.0");
        
        verify(legacyService).updateMcpServer(NAMESPACE_ID, true, server, tools, resources,
            endpoint, false);
        verify(legacyService).deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, "1.0.0");
        verifyNoInteractions(lifecycleService);
    }
    
    @Test
    void testRoutesCompleteContractToLifecycleAfterManagedCutover() throws Exception {
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        Page<McpServerBasicInfo> page = new Page<>();
        McpServerDetailInfo detail = new McpServerDetailInfo();
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        when(lifecycleService.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME, "blur", 2, 20))
            .thenReturn(page);
        when(lifecycleService.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, null))
            .thenReturn(detail);
        when(lifecycleService.getServingMcpServerDetail(NAMESPACE_ID, MCP_NAME, null))
            .thenReturn(detail);
        when(lifecycleService.createMcpServer(NAMESPACE_ID, server, tools, resources, endpoint))
            .thenReturn(MCP_ID);
        
        assertEquals(page, service.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME, "blur", 2, 20));
        assertEquals(detail,
            service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, null));
        assertEquals(detail,
            service.getServingMcpServerDetail(NAMESPACE_ID, MCP_NAME, null));
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, server, tools, resources, endpoint));
        service.updateMcpServer(NAMESPACE_ID, false, server, tools, resources, endpoint, true);
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, null);
        
        verify(lifecycleService).updateMcpServer(NAMESPACE_ID, false, server, tools, resources,
            endpoint, true);
        verify(lifecycleService).deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, null);
        verifyNoInteractions(legacyService);
    }
    
    @Test
    void testConvenienceOverloadsKeepCompleteContractRouting() throws Exception {
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.SYNCING);
        when(legacyService.createMcpServer(NAMESPACE_ID, server, tools, null, endpoint))
            .thenReturn(MCP_ID);
        
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, server, tools, endpoint));
        service.updateMcpServer(NAMESPACE_ID, true, server, tools, endpoint, false);
        
        verify(legacyService).updateMcpServer(NAMESPACE_ID, true, server, tools, null, endpoint,
            false);
        verifyNoInteractions(lifecycleService);
    }
    
    @Test
    void testRejectsManagedTrafficOnMemberWithoutCapability() {
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        when(modeResolver.localMemberSupportsManagedLifecycle()).thenReturn(false);
        
        assertThrows(NacosApiException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, null));
        assertThrows(NacosApiException.class,
            () -> service.getServingMcpServerDetail(NAMESPACE_ID, MCP_NAME, null));
        verifyNoInteractions(legacyService, lifecycleService);
    }
    
    @Test
    void testRoutesStandardLifecycleContractOnlyAfterManagedCutover() throws Exception {
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        Page<McpServerVersionSummary> page = new Page<>();
        McpServerVersionDetail detail = new McpServerVersionDetail();
        McpServerVersionSummary summary = new McpServerVersionSummary();
        Map<String, String> labels = Map.of("stable", "1.0.0");
        when(lifecycleService.listMcpServerVersions(NAMESPACE_ID, MCP_NAME, "draft", 1, 10))
            .thenReturn(page);
        when(lifecycleService.getMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(detail);
        when(lifecycleService.createMcpServerDraft(NAMESPACE_ID, server, tools, resources,
            endpoint)).thenReturn(detail);
        when(lifecycleService.updateMcpServerDraft(NAMESPACE_ID, server, tools, resources,
            endpoint)).thenReturn(detail);
        when(lifecycleService.submitMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(summary);
        when(lifecycleService.publishMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(summary);
        when(lifecycleService.forcePublishMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(summary);
        when(lifecycleService.redraftMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(summary);
        when(lifecycleService.onlineLifecycleVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(summary);
        when(lifecycleService.offlineLifecycleVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"))
            .thenReturn(summary);
        when(lifecycleService.updateMcpServerLabels(NAMESPACE_ID, MCP_NAME, labels))
            .thenReturn(labels);
        
        assertEquals(page,
            service.listMcpServerVersions(NAMESPACE_ID, MCP_NAME, "draft", 1, 10));
        assertEquals(detail,
            service.getMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(detail,
            service.createMcpServerDraft(NAMESPACE_ID, server, tools, resources, endpoint));
        assertEquals(detail,
            service.updateMcpServerDraft(NAMESPACE_ID, server, tools, resources, endpoint));
        service.deleteMcpServerDraft(NAMESPACE_ID, MCP_NAME, "1.0.0");
        assertEquals(summary,
            service.submitMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(summary,
            service.publishMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(summary,
            service.forcePublishMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(summary,
            service.redraftMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(summary,
            service.onlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(summary,
            service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        assertEquals(labels, service.updateMcpServerLabels(NAMESPACE_ID, MCP_NAME, labels));
        
        verify(lifecycleService).deleteMcpServerDraft(NAMESPACE_ID, MCP_NAME, "1.0.0");
        verifyNoInteractions(legacyService);
    }
    
    @Test
    void testRejectsStandardLifecycleContractBeforeManagedCutover() {
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.SYNCING);
        
        assertThrows(NacosApiException.class,
            () -> service.getMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        
        verifyNoInteractions(legacyService, lifecycleService);
    }
    
    @Test
    void testRejectsStandardLifecycleContractOnMemberWithoutCapability() {
        when(modeResolver.resolve()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        when(modeResolver.localMemberSupportsManagedLifecycle()).thenReturn(false);
        
        assertThrows(NacosApiException.class,
            () -> service.getMcpServerVersion(NAMESPACE_ID, MCP_NAME, "1.0.0"));
        
        verifyNoInteractions(legacyService, lifecycleService);
    }
    
    @Test
    void testResolverUsesDurableManagementState() {
        McpLifecycleManagementStateService stateService =
            org.mockito.Mockito.mock(McpLifecycleManagementStateService.class);
        when(stateService.resolveMode()).thenReturn(McpCompatibilityMode.SYNCING);
        assertEquals(McpCompatibilityMode.SYNCING,
            new McpCompatibilityModeResolver(stateService).resolve());
    }
}
