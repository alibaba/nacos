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

package com.alibaba.nacos.mcpregistry.service;

import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.mcpregistry.form.ListServerForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosMcpRegistryServiceTest {
    
    private static final String RANDOM_NAMESPACE_ID = UUID.randomUUID().toString();
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private McpServerIndex mcpServerIndex;
    
    NacosMcpRegistryService mcpRegistryService;
    
    @BeforeEach
    void setUp() {
        mcpRegistryService = new NacosMcpRegistryService(mcpServerOperationService, namespaceOperationService,
                mcpServerIndex);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listMcpServersWithZeroOffset() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(0);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertTrue(actual.getServers().isEmpty());
    }
    
    @Test
    void listMcpServersWithOffsetLargeThenTotalCount() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(100);
        listServerForm.setLimit(10);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertTrue(actual.getServers().isEmpty());
    }
    
    @Test
    void listMcpServersWithoutOffsetAndLargeOffset() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(100);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getServers().size());
    }
    
    @Test
    void listMcpServerWithoutOffsetAndSmallLimit() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(5);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(5, actual.getServers().size());
        for (ServerResponse each : actual.getServers()) {
            assertTrue(each.getServer().getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
    }
    
    @Test
    void listMcpServerWithoutOffsetAndLimitOverNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(11);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(11, actual.getServers().size());
        for (int i = 0; i < 10; i++) {
            assertTrue(actual.getServers().get(i).getServer().getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
        assertTrue(actual.getServers().get(10).getServer().getDescription().endsWith(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE));
    }
    
    @Test
    void listMcpServerWithOffsetAndLargeLimit() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(5);
        listServerForm.setLimit(100);
        mockMultipleNamespace();
        // After sorting, RANDOM_NAMESPACE_ID comes first, then public
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        // offset=5, first ns has 10, so get 10-5=5, then second ns has 2, so get 2. Total 7.
        assertEquals(7, actual.getServers().size());
    }
    
    @Test
    void listMcpServerWithOffsetAndSmallLimit() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(5);
        listServerForm.setLimit(4);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(4, actual.getServers().size());
        for (ServerResponse each : actual.getServers()) {
            assertTrue(each.getServer().getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
    }
    
    @Test
    void listMcpServerWithOffsetAndLimitOverNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(5);
        listServerForm.setLimit(6);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(6, actual.getServers().size());
        for (int i = 0; i < 5; i++) {
            assertTrue(actual.getServers().get(i).getServer().getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
        assertTrue(actual.getServers().get(5).getServer().getDescription().endsWith(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE));
    }
    
    @Test
    void listMcpServerWithOffsetOverNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(10);
        listServerForm.setLimit(10);
        mockMultipleNamespace();
        mockListMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 2);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(2, actual.getServers().size());
        for (ServerResponse each : actual.getServers()) {
            assertTrue(each.getServer().getDescription().endsWith(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE));
        }
    }
    
    @Test
    void listMcpServerForTargetNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(30);
        listServerForm.setNamespaceId(RANDOM_NAMESPACE_ID);
        listServerForm.setServerName(null);
        mockListMcpServerWithPage(RANDOM_NAMESPACE_ID, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(10, actual.getServers().size());
    }
    
    @Test
    void getServerNotFound() throws NacosException {
        String serverName = "nonExistentServer";
        assertNull(mcpRegistryService.getServer(serverName, null, null));
    }
    
    @Test
    void getServerWithoutBackendEndpoints() throws NacosException {
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(RANDOM_NAMESPACE_ID, null, id, null)).thenReturn(
                mockMcpServerDetailInfo(id, RANDOM_NAMESPACE_ID, false, false));
        ServerResponse result = mcpRegistryService.getServer(id, RANDOM_NAMESPACE_ID, null);
        assertNotNull(result);
        assertNotNull(result.getServer());
        assertEquals("mockMcpServer", result.getServer().getName());
        assertEquals("Description:" + RANDOM_NAMESPACE_ID, result.getServer().getDescription());
        assertNull(result.getServer().getRepository());
        assertEquals("1.0.0", result.getServer().getVersion());
        assertNotNull(result.getMeta());
        assertNotNull(result.getMeta().getOfficial());
        assertEquals("2025-06-10T02:29:17Z", result.getMeta().getOfficial().getPublishedAt());
        assertNull(result.getServer().getRemotes());
    }

    @Test
    void getServerWithBackendEndpoints() throws NacosException {
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(RANDOM_NAMESPACE_ID, null, id, null)).thenReturn(
                mockMcpServerDetailInfo(id, RANDOM_NAMESPACE_ID, true, false));
        ServerResponse result = mcpRegistryService.getServer(id, RANDOM_NAMESPACE_ID, null);
        assertNotNull(result);
        assertNotNull(result.getServer());
        assertEquals("mockMcpServer", result.getServer().getName());
        assertEquals("Description:" + RANDOM_NAMESPACE_ID, result.getServer().getDescription());
        assertNull(result.getServer().getRepository());
        assertEquals("1.0.0", result.getServer().getVersion());
        assertNotNull(result.getMeta());
        assertNotNull(result.getMeta().getOfficial());
        assertEquals("2025-06-10T02:29:17Z", result.getMeta().getOfficial().getPublishedAt());
        assertNotNull(result.getServer().getRemotes());
        assertEquals(1, result.getServer().getRemotes().size());
        assertEquals("sse", result.getServer().getRemotes().get(0).getType());
        assertEquals("http://127.0.0.1:8080/api/path", result.getServer().getRemotes().get(0).getUrl());
    }

    @Test
    void getToolsNotFound() throws NacosException {
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(null, null, id, null)).thenReturn(null);
        assertNull(mcpRegistryService.getTools(id, null));
    }
    
    @Test
    void getTools() throws NacosException {
        String id = UUID.randomUUID().toString();
        when(mcpServerOperationService.getMcpServerDetail(null, id, null, null)).thenReturn(
                mockMcpServerDetailInfo(id, RANDOM_NAMESPACE_ID, false, true));
        when(mcpServerIndex.getMcpServerById(eq(id))).thenReturn(new McpServerIndexData());
        assertNotNull(mcpRegistryService.getTools(id, null));
    }
    
    private void mockMultipleNamespace() {
        Namespace namespace1 = new Namespace(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Namespace namespace2 = new Namespace(RANDOM_NAMESPACE_ID, "test");
        List<Namespace> namespaces = List.of(namespace1, namespace2);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaces);
    }
    
    private void mockListMcpServerWithPage(String namespaceId, int totalCount) {
        List<McpServerBasicInfo> allServers = new LinkedList<>();
        for (int i = 0; i < totalCount; i++) {
            McpServerBasicInfo basicInfo = mockMcpServerBasicInfo(i, namespaceId);
            allServers.add(basicInfo);
            // ensure getServer won't return null by mocking detail lookup for each generated name
            try {
                Mockito.lenient()
                        .when(mcpServerOperationService.getMcpServerDetail(namespaceId, null, basicInfo.getName(), null))
                        .thenReturn(mockMcpServerDetailInfo(basicInfo.getId(), namespaceId, false, false));
            } catch (NacosException e) {
                throw new RuntimeException(e);
            }
        }
        
        // Mock listMcpServerWithPage for any pageNum, pageSize, and serverName combination
        Mockito.lenient().when(mcpServerOperationService.listMcpServerWithPage(eq(namespaceId), Mockito.any(), 
                Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
                .thenAnswer(invocation -> {
                    int pageNum = invocation.getArgument(3);
                    int pageSize = invocation.getArgument(4);
                    Page<McpServerBasicInfo> dataPage = new Page<>();
                    dataPage.setPageNumber(pageNum);
                    dataPage.setTotalCount(totalCount);
                    
                    // Get items for this page from the full list
                    int pageStart = (pageNum - 1) * pageSize;
                    int pageEnd = Math.min(pageStart + pageSize, totalCount);
                    
                    if (pageStart < totalCount && pageEnd > pageStart) {
                        List<McpServerBasicInfo> pageItems = new LinkedList<>(allServers.subList(pageStart, pageEnd));
                        dataPage.setPageItems(pageItems);
                    } else {
                        dataPage.setPageItems(new LinkedList<>());
                    }
                    
                    return dataPage;
                });
    }
    
    private McpServerBasicInfo mockMcpServerBasicInfo(int number, String namespaceId) {
        String id = UUID.randomUUID().toString();
        String actualServerName = "mockMcpServer:" + number;
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setId(id);
        result.setNamespaceId(namespaceId);
        result.setName(actualServerName);
        result.setDescription("Description:" + namespaceId);
        result.setVersionDetail(new ServerVersionDetail());
        result.getVersionDetail().setVersion("1.0.0");
        result.getVersionDetail().setIs_latest(true);
        result.getVersionDetail().setRelease_date("2025-06-10T02:29:17Z");
        return result;
    }
    
    private McpServerDetailInfo mockMcpServerDetailInfo(String id, String namespaceId, boolean withBackendEndpoints,
            boolean withTools) {
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setId(id);
        result.setName("mockMcpServer");
        result.setDescription("Description:" + namespaceId);
        result.setVersionDetail(new ServerVersionDetail());
        result.getVersionDetail().setVersion("1.0.0");
        result.getVersionDetail().setIs_latest(true);
        result.getVersionDetail().setRelease_date("2025-06-10T02:29:17Z");

        // Set allVersions for buildMeta to work correctly
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        versionDetail.setIs_latest(true);
        versionDetail.setRelease_date("2025-06-10T02:29:17Z");
        result.setAllVersions(new LinkedList<>(java.util.List.of(versionDetail)));

        result.setFrontProtocol(
                withBackendEndpoints ? AiConstants.Mcp.MCP_PROTOCOL_SSE : AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        if (withBackendEndpoints) {
            McpEndpointInfo mcpEndpointInfo = new McpEndpointInfo();
            mcpEndpointInfo.setProtocol("http");
            mcpEndpointInfo.setAddress("127.0.0.1");
            mcpEndpointInfo.setPort(8080);
            mcpEndpointInfo.setPath("/api/path");
            List<McpEndpointInfo> endpoints = new LinkedList<>();
            endpoints.add(mcpEndpointInfo);
            result.setBackendEndpoints(endpoints);
        }
        if (withTools) {
            result.setToolSpec(new McpToolSpecification());
        }
        return result;
    }
}