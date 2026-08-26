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

import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpManagementReadServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_NAME = "demo-mcp";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Mock
    private McpServerOperationService legacyReadService;
    
    @Mock
    private McpLifecycleReadService lifecycleReadService;
    
    @Mock
    private McpLifecycleManagementStateService managementStateService;
    
    private McpManagementReadService readService;
    
    @BeforeEach
    void setUp() {
        readService = new McpManagementReadService(legacyReadService, lifecycleReadService,
            managementStateService);
    }
    
    @Test
    void testSyncingListUsesLegacyPath() throws Exception {
        Page<McpServerBasicInfo> expected = new Page<>();
        when(managementStateService.isLifecycleManaged()).thenReturn(false);
        when(legacyReadService.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME, "accurate", 1, 10))
            .thenReturn(expected);
        
        assertSame(expected,
            readService.listMcpServers(NAMESPACE_ID, MCP_NAME, "accurate", 1, 10));
        verify(lifecycleReadService, never()).listMcpServers(NAMESPACE_ID, MCP_NAME, "accurate",
            1, 10);
    }
    
    @Test
    void testManagedListUsesLifecyclePath() throws Exception {
        Page<McpServerBasicInfo> expected = new Page<>();
        when(managementStateService.isLifecycleManaged()).thenReturn(true);
        when(lifecycleReadService.listMcpServers(NAMESPACE_ID, MCP_NAME, "blur", 2, 20))
            .thenReturn(expected);
        
        assertSame(expected, readService.listMcpServers(NAMESPACE_ID, MCP_NAME, "blur", 2, 20));
        verify(legacyReadService, never()).listMcpServerWithPage(NAMESPACE_ID, MCP_NAME, "blur", 2,
            20);
    }
    
    @Test
    void testManagedGetDelegatesCanonicalLifecycleRead() throws Exception {
        McpServerDetailInfo expected = detail();
        when(managementStateService.isLifecycleManaged()).thenReturn(true);
        when(lifecycleReadService.getMcpServer(NAMESPACE_ID, null, MCP_ID, null))
            .thenReturn(expected);
        
        assertSame(expected, readService.getMcpServer(NAMESPACE_ID, null, MCP_ID, null));
        verify(legacyReadService, never()).getMcpServerDetail(NAMESPACE_ID, MCP_ID, null, null);
    }
    
    @Test
    void testSyncingNameReadUsesLegacyPathWithoutLifecycleLookup() throws Exception {
        McpServerDetailInfo expected = detail();
        when(managementStateService.isLifecycleManaged()).thenReturn(false);
        when(legacyReadService.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, "1.0.0"))
            .thenReturn(expected);
        
        assertSame(expected,
            readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, "1.0.0"));
        verify(lifecycleReadService, never()).getMcpServer(NAMESPACE_ID, MCP_NAME, null, "1.0.0");
    }
    
    @Test
    void testSyncingIdReadUsesLegacyPathWithoutLifecycleLookup() throws Exception {
        McpServerDetailInfo expected = detail();
        when(managementStateService.isLifecycleManaged()).thenReturn(false);
        when(legacyReadService.getMcpServerDetail(NAMESPACE_ID, MCP_ID, null, null))
            .thenReturn(expected);
        
        assertSame(expected, readService.getMcpServer(NAMESPACE_ID, null, MCP_ID, null));
        verify(lifecycleReadService, never()).getMcpServer(NAMESPACE_ID, null, MCP_ID, null);
    }
    
    private McpServerDetailInfo detail() {
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        return result;
    }
}
