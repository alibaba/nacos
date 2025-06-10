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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServer;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.NacosMcpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.mcpregistry.form.GetServerForm;
import com.alibaba.nacos.mcpregistry.form.ListServerForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 1, 2, 0);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 0, 1, 10, 0);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertTrue(actual.getServers().isEmpty());
    }
    
    @Test
    void listMcpServersWithOffsetLargeThenTotalCount() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(100);
        listServerForm.setLimit(10);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 90, 10, 2, 0);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 100, 10, 10, 0);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertTrue(actual.getServers().isEmpty());
    }
    
    @Test
    void listMcpServersWithoutOffsetAndLargeOffset() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(100);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 90, 2, 2);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 0, 100, 10, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(12, actual.getServers().size());
    }
    
    @Test
    void listMcpServerWithoutOffsetAndSmallLimit() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(5);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 1, 2, 0);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 0, 5, 10, 5);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(5, actual.getServers().size());
        for (McpRegistryServer each : actual.getServers()) {
            assertTrue(each.getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
    }
    
    @Test
    void listMcpServerWithoutOffsetAndLimitOverNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(11);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 1, 2, 1);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 0, 11, 10, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(11, actual.getServers().size());
        for (int i = 0; i < 10; i++) {
            assertTrue(actual.getServers().get(i).getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
        assertTrue(actual.getServers().get(10).getDescription().endsWith(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE));
    }
    
    @Test
    void listMcpServerWithOffsetAndLargeLimit() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(5);
        listServerForm.setLimit(100);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 90, 2, 2);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 5, 100, 10, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(12, actual.getServers().size());
    }
    
    @Test
    void listMcpServerWithOffsetAndSmallLimit() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(5);
        listServerForm.setLimit(4);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 1, 2, 1);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 5, 4, 10, 4);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(4, actual.getServers().size());
        for (McpRegistryServer each : actual.getServers()) {
            assertTrue(each.getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
    }
    
    @Test
    void listMcpServerWithOffsetAndLimitOverNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(5);
        listServerForm.setLimit(6);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 1, 2, 1);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 5, 6, 10, 5);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(6, actual.getServers().size());
        for (int i = 0; i < 5; i++) {
            assertTrue(actual.getServers().get(i).getDescription().endsWith(RANDOM_NAMESPACE_ID));
        }
        assertTrue(actual.getServers().get(5).getDescription().endsWith(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE));
    }
    
    @Test
    void listMcpServerWithOffsetOverNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(10);
        listServerForm.setLimit(10);
        mockMultipleNamespace();
        mockListMcpServerWithOffset(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, 0, 10, 2, 2);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 10, 10, 10, 0);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(12, actual.getTotal_count());
        assertEquals(2, actual.getServers().size());
        for (McpRegistryServer each : actual.getServers()) {
            assertTrue(each.getDescription().endsWith(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE));
        }
    }
    
    @Test
    void listMcpServerForTargetNamespace() {
        ListServerForm listServerForm = new ListServerForm();
        listServerForm.setOffset(0);
        listServerForm.setLimit(30);
        listServerForm.setNamespaceId(RANDOM_NAMESPACE_ID);
        listServerForm.setServerName(null);
        mockListMcpServerWithOffset(RANDOM_NAMESPACE_ID, 0, 30, 10, 10);
        McpRegistryServerList actual = mcpRegistryService.listMcpServers(listServerForm);
        assertEquals(10, actual.getTotal_count());
        assertEquals(10, actual.getServers().size());
    }
    
    @Test
    void getServerNotFound() throws NacosException {
        String id = UUID.randomUUID().toString();
        assertNull(mcpRegistryService.getServer(id, new GetServerForm()));
    }
    
    @Test
    void getServerWithoutBackendEndpoints() throws NacosException {
        String id = UUID.randomUUID().toString();
        McpServerIndexData mcpServerIndexData = new McpServerIndexData();
        mcpServerIndexData.setId(id);
        mcpServerIndexData.setNamespaceId(RANDOM_NAMESPACE_ID);
        when(mcpServerIndex.getMcpServerById(id)).thenReturn(mcpServerIndexData);
        when(mcpServerOperationService.getMcpServerDetail(RANDOM_NAMESPACE_ID, id, null, null)).thenReturn(
                mockMcpServerDetailInfo(id, RANDOM_NAMESPACE_ID, false, false));
        McpRegistryServerDetail result = mcpRegistryService.getServer(id, new GetServerForm());
        assertEquals(id, result.getId());
        assertEquals("mockMcpServer", result.getName());
        assertEquals("Description:" + RANDOM_NAMESPACE_ID, result.getDescription());
        assertNull(result.getRepository());
        assertEquals("1.0.0", result.getVersion_detail().getVersion());
        assertTrue(result.getVersion_detail().getIs_latest());
        assertEquals("2025-06-10T02:29:17Z", result.getVersion_detail().getRelease_date());
        assertNull(result.getRemotes());
    }
    
    @Test
    void getServerWithBackendEndpoints() throws NacosException {
        String id = UUID.randomUUID().toString();
        McpServerIndexData mcpServerIndexData = new McpServerIndexData();
        mcpServerIndexData.setId(id);
        mcpServerIndexData.setNamespaceId(RANDOM_NAMESPACE_ID);
        when(mcpServerIndex.getMcpServerById(id)).thenReturn(mcpServerIndexData);
        when(mcpServerOperationService.getMcpServerDetail(RANDOM_NAMESPACE_ID, id, null, null)).thenReturn(
                mockMcpServerDetailInfo(id, RANDOM_NAMESPACE_ID, true, false));
        McpRegistryServerDetail result = mcpRegistryService.getServer(id, new GetServerForm());
        assertEquals(id, result.getId());
        assertEquals("mockMcpServer", result.getName());
        assertEquals("Description:" + RANDOM_NAMESPACE_ID, result.getDescription());
        assertNull(result.getRepository());
        assertEquals("1.0.0", result.getVersion_detail().getVersion());
        assertTrue(result.getVersion_detail().getIs_latest());
        assertEquals("2025-06-10T02:29:17Z", result.getVersion_detail().getRelease_date());
        assertNotNull(result.getRemotes());
        assertEquals(1, result.getRemotes().size());
        assertEquals("sse", result.getRemotes().get(0).getTransport_type());
        assertEquals("http://127.0.0.1:8080/api/path", result.getRemotes().get(0).getUrl());
    }
    
    @Test
    void getToolsNotFound() throws NacosException {
        String id = UUID.randomUUID().toString();
        assertNull(mcpRegistryService.getTools(id, null));
    }
    
    @Test
    void getTools() throws NacosException {
        String id = UUID.randomUUID().toString();
        McpServerIndexData mcpServerIndexData = new McpServerIndexData();
        mcpServerIndexData.setId(id);
        mcpServerIndexData.setNamespaceId(RANDOM_NAMESPACE_ID);
        when(mcpServerIndex.getMcpServerById(id)).thenReturn(mcpServerIndexData);
        when(mcpServerOperationService.getMcpServerDetail(RANDOM_NAMESPACE_ID, id, null, null)).thenReturn(
                mockMcpServerDetailInfo(id, RANDOM_NAMESPACE_ID, false, true));
        assertNotNull(mcpRegistryService.getTools(id, null));
    }
    
    @Test
    void createMcpServer() throws NacosException {
        McpServerBasicInfo mockInfo = mockMcpServerBasicInfo(0, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        NacosMcpRegistryServerDetail serverDetail = new NacosMcpRegistryServerDetail();
        serverDetail.setNacosNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        serverDetail.setName(mockInfo.getName());
        serverDetail.setDescription(mockInfo.getDescription());
        serverDetail.setVersion_detail(mockInfo.getVersionDetail());
        serverDetail.setNacosMcpEndpointSpec(new McpEndpointSpec());
        mcpRegistryService.createMcpServer(serverDetail);
        verify(mcpServerOperationService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                any(McpServerBasicInfo.class), isNull(), any(McpEndpointSpec.class));
    }
    
    @Test
    void updateMcpServerNonExist() {
        assertThrows(NacosApiException.class,
                () -> mcpRegistryService.updateMcpServer(new NacosMcpRegistryServerDetail()));
    }
    
    @Test
    void updateMcpServer() throws NacosException {
        McpServerBasicInfo mockInfo = mockMcpServerBasicInfo(0, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        NacosMcpRegistryServerDetail serverDetail = new NacosMcpRegistryServerDetail();
        serverDetail.setNacosNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        serverDetail.setId(mockInfo.getId());
        serverDetail.setName(mockInfo.getName());
        serverDetail.setDescription(mockInfo.getDescription());
        serverDetail.setVersion_detail(mockInfo.getVersionDetail());
        serverDetail.setNacosMcpEndpointSpec(new McpEndpointSpec());
        McpServerIndexData mockIndex = new McpServerIndexData();
        mockIndex.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        mockIndex.setId(mockInfo.getId());
        when(mcpServerIndex.getMcpServerById(mockInfo.getId())).thenReturn(mockIndex);
        mcpRegistryService.updateMcpServer(serverDetail);
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(true),
                any(McpServerBasicInfo.class), isNull(), any(McpEndpointSpec.class));
    }
    
    private void mockMultipleNamespace() {
        Namespace namespace1 = new Namespace(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Namespace namespace2 = new Namespace(RANDOM_NAMESPACE_ID, "test");
        List<Namespace> namespaces = List.of(namespace1, namespace2);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaces);
    }
    
    private void mockListMcpServerWithOffset(String namespaceId, int offset, int limit, int totalCount,
            int actualSize) {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        mockPage.setTotalCount(totalCount);
        List<McpServerBasicInfo> mcpServerBasicInfos = new LinkedList<>();
        mockPage.setPageItems(mcpServerBasicInfos);
        for (int i = 0; i < actualSize; i++) {
            mockPage.getPageItems().add(mockMcpServerBasicInfo(i, namespaceId));
        }
        when(mcpServerOperationService.listMcpServerWithOffset(namespaceId, null, Constants.MCP_LIST_SEARCH_BLUR,
                offset, limit)).thenReturn(mockPage);
    }
    
    private McpServerBasicInfo mockMcpServerBasicInfo(int number, String namespaceId) {
        String id = UUID.randomUUID().toString();
        String actualServerName = "mockMcpServer:" + number;
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setId(id);
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
        result.setFrontProtocol(
                withBackendEndpoints ? AiConstants.Mcp.MCP_PROTOCOL_SSE : AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        if (withBackendEndpoints) {
            McpEndpointInfo mcpEndpointInfo = new McpEndpointInfo();
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