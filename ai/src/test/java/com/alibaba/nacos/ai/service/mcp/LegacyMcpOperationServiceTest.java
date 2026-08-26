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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyMcpOperationServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_NAME = "demo-mcp";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final String VERSION_ONE = "1.0.0";
    
    private static final String VERSION_TWO = "2.0.0";
    
    @Mock
    private McpServingManifestStorage manifestStorage;
    
    @Mock
    private McpVersionStorageService versionStorageService;
    
    @Mock
    private McpEndpointOperationService endpointOperationService;
    
    @Mock
    private McpServerIndex mcpServerIndex;
    
    @Mock
    private McpHistoricalResourceReconciler reconciler;
    
    @Mock
    private AiResourceIndexMaintenanceService indexMaintenanceService;
    
    private LegacyMcpOperationService service;
    
    @BeforeEach
    void setUp() {
        service = new LegacyMcpOperationService(manifestStorage, versionStorageService,
            endpointOperationService, mcpServerIndex, reconciler);
        service.setIndexMaintenanceService(indexMaintenanceService);
    }
    
    @Test
    void testListAndDetailReadOnlyThroughManifestAndVersionStorage() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION_ONE);
        McpServerIndexData index = McpServerIndexData.newIndexData(MCP_ID, NAMESPACE_ID);
        Page<McpServerIndexData> indexPage = new Page<>();
        indexPage.setPageItems(Collections.singletonList(index));
        indexPage.setTotalCount(1);
        indexPage.setPagesAvailable(1);
        when(mcpServerIndex.searchMcpServerByNameWithPage(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10)).thenReturn(indexPage);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        stubVersionContent(VERSION_ONE, true, true);
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        when(versionStorageService.load(any())).thenReturn(new McpVersionStorageContents(
            serverBytes(server(VERSION_ONE, true, true)), bytes(JacksonUtils.toJson(tools)),
            bytes(JacksonUtils.toJson(resources))));
        
        Page<McpServerBasicInfo> page = service.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10);
        McpServerDetailInfo detail = service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME,
            null);
        
        assertEquals(1, page.getTotalCount());
        assertEquals(MCP_NAME, page.getPageItems().get(0).getName());
        assertEquals(VERSION_ONE, detail.getVersion());
        assertTrue(Boolean.TRUE.equals(detail.getVersionDetail().getIs_latest()));
        assertNotNull(detail.getToolSpec());
        assertNotNull(detail.getResourceSpec());
        verify(endpointOperationService, never()).injectEndpoint(any());
    }
    
    @Test
    void testCreateUsesStorageThenManifestAndReconcilesByCanonicalName() throws Exception {
        McpToolSpecification tools = new McpToolSpecification();
        tools.setTools(Collections.emptyList());
        McpResourceSpecification resources = new McpResourceSpecification();
        resources.setResources(Collections.singletonList(Map.of("name", "resource")));
        McpEndpointSpec endpoint = new McpEndpointSpec();
        endpoint.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpoint.setData(Map.of(Constants.MCP_BACKEND_INSTANCE_PROTOCOL_KEY,
            AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE));
        when(endpointOperationService.createMcpServerEndpointServiceIfNecessary(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE, endpoint, false)).thenReturn(
                Service.newService(NAMESPACE_ID, "mcp-endpoint", MCP_NAME + "::" + VERSION_ONE));
        McpServerBasicInfo specification = specification(VERSION_ONE, "first");
        specification.setId(MCP_ID);
        
        String result = service.createMcpServer(NAMESPACE_ID, specification, tools, resources,
            endpoint);
        
        assertEquals(MCP_ID, result);
        ArgumentCaptor<McpVersionStorageDescriptor> descriptor =
            ArgumentCaptor.forClass(McpVersionStorageDescriptor.class);
        ArgumentCaptor<McpVersionStorageContents> contents =
            ArgumentCaptor.forClass(McpVersionStorageContents.class);
        verify(versionStorageService).save(descriptor.capture(), contents.capture());
        assertNotNull(descriptor.getValue().getToolKey());
        assertNotNull(descriptor.getValue().getResourceKey());
        McpServerStorageInfo stored = JacksonUtils.toObj(
            new String(contents.getValue().getServerContent(), StandardCharsets.UTF_8),
            McpServerStorageInfo.class);
        assertEquals(MCP_NAME + "::" + VERSION_ONE,
            stored.getRemoteServerConfig().getServiceRef().getServiceName());
        ArgumentCaptor<McpServerVersionInfo> manifest =
            ArgumentCaptor.forClass(McpServerVersionInfo.class);
        verify(manifestStorage).publish(eq(NAMESPACE_ID), manifest.capture());
        assertEquals(VERSION_ONE, manifest.getValue().getLatestPublishedVersion());
        InOrder publishOrder = inOrder(versionStorageService, manifestStorage, reconciler,
            indexMaintenanceService);
        publishOrder.verify(versionStorageService).save(any(McpVersionStorageDescriptor.class),
            any(McpVersionStorageContents.class));
        publishOrder.verify(manifestStorage).publish(eq(NAMESPACE_ID),
            any(McpServerVersionInfo.class));
        publishOrder.verify(reconciler).reconcile(eq(NAMESPACE_ID),
            any(McpServerVersionInfo.class));
        publishOrder.verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME);
        verify(reconciler).reconcile(NAMESPACE_ID, manifest.getValue());
        verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME);
    }
    
    @Test
    void testCreateKeepsHistoricalSuccessWhenLifecycleReconciliationFails() throws Exception {
        McpServerBasicInfo specification = specification(VERSION_ONE, "first");
        specification.setId(MCP_ID);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "reconcile failed"))
            .when(reconciler).reconcile(eq(NAMESPACE_ID), any(McpServerVersionInfo.class));
        
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, specification, null, null, null));
        
        verify(mcpServerIndex).removeMcpServerByName(NAMESPACE_ID, MCP_NAME);
        verify(mcpServerIndex).removeMcpServerById(MCP_ID);
        verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME);
    }
    
    @Test
    void testUpdatePreservesLatestUntilPublishAndDeletesObsoleteContent() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION_ONE);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        when(mcpServerIndex.getMcpServerByName(NAMESPACE_ID, MCP_NAME))
            .thenReturn(McpServerIndexData.newIndexData(MCP_ID, NAMESPACE_ID));
        stubVersionContent(VERSION_TWO, true, true);
        McpServerBasicInfo second = specification(VERSION_TWO, "second");
        
        service.updateMcpServer(NAMESPACE_ID, false, second, null, null, null, false);
        
        ArgumentCaptor<McpServerVersionInfo> draftManifest =
            ArgumentCaptor.forClass(McpServerVersionInfo.class);
        verify(manifestStorage).publish(eq(NAMESPACE_ID), draftManifest.capture());
        assertEquals(VERSION_ONE, draftManifest.getValue().getLatestPublishedVersion());
        assertEquals(2, draftManifest.getValue().getVersionDetails().size());
        verify(versionStorageService).deleteObsolete(any(), any());
        verify(reconciler).reconcile(NAMESPACE_ID, draftManifest.getValue());
    }
    
    @Test
    void testDeleteVersionAndFullResourceConvergeRowsAfterLegacyStorage() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION_ONE, VERSION_TWO);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, VERSION_TWO);
        
        ArgumentCaptor<McpVersionStorageDescriptor> deletedDescriptor =
            ArgumentCaptor.forClass(McpVersionStorageDescriptor.class);
        verify(versionStorageService).delete(deletedDescriptor.capture());
        assertNotNull(deletedDescriptor.getValue().getToolKey());
        assertNotNull(deletedDescriptor.getValue().getResourceKey());
        ArgumentCaptor<McpServerVersionInfo> remaining =
            ArgumentCaptor.forClass(McpServerVersionInfo.class);
        verify(manifestStorage).publish(eq(NAMESPACE_ID), remaining.capture());
        assertEquals(List.of(VERSION_ONE), remaining.getValue().getVersionDetails().stream()
            .map(ServerVersionDetail::getVersion).toList());
        verify(reconciler).reconcileAfterLegacyDelete(NAMESPACE_ID, MCP_NAME, MCP_ID,
            remaining.getValue());
        verify(endpointOperationService).deleteMcpServerEndpointService(NAMESPACE_ID,
            MCP_NAME + "::" + VERSION_TWO);
        
        McpServerVersionInfo single = manifest(VERSION_ONE);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(single);
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, null);
        verify(manifestStorage).delete(NAMESPACE_ID, MCP_ID);
        verify(reconciler).reconcileAfterLegacyDelete(NAMESPACE_ID, MCP_NAME, MCP_ID, null);
    }
    
    @Test
    void testRejectsConflictingIdentityAndMissingVersion() throws Exception {
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest(VERSION_ONE));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, "other", null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, VERSION_TWO));
        
        McpServerBasicInfo invalid = specification(VERSION_ONE, "first");
        invalid.setId("not-a-uuid");
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, invalid, null, null, null));
    }
    
    @Test
    void testListHandlesMissingPageAndRejectsInvalidIndexRows() throws Exception {
        when(mcpServerIndex.searchMcpServerByNameWithPage(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 2, 10)).thenReturn(null);
        
        Page<McpServerBasicInfo> empty = service.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 2, 10);
        
        assertEquals(0, empty.getTotalCount());
        assertEquals(0, empty.getPagesAvailable());
        assertEquals(2, empty.getPageNumber());
        
        Page<McpServerIndexData> invalidPage = new Page<>();
        invalidPage.setPageItems(Collections.singletonList(new McpServerIndexData()));
        when(mcpServerIndex.searchMcpServerByNameWithPage(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10)).thenReturn(invalidPage);
        assertThrows(NacosException.class,
            () -> service.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10));
    }
    
    @Test
    void testDetailRejectsStoredIdentityAndInjectsNonStdioEndpoint() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION_ONE);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        McpServerStorageInfo wrongName = server(VERSION_ONE, false, false);
        wrongName.setName("other");
        when(versionStorageService.loadIfPresent(any())).thenReturn(
            new McpVersionStorageContents(serverBytes(wrongName), null, null));
        when(versionStorageService.load(any())).thenReturn(
            new McpVersionStorageContents(serverBytes(wrongName), null, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, VERSION_ONE));
        
        McpServerStorageInfo streamable = server(VERSION_ONE, false, false);
        streamable.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        when(versionStorageService.loadIfPresent(any())).thenReturn(
            new McpVersionStorageContents(serverBytes(streamable), null, null));
        when(versionStorageService.load(any())).thenReturn(
            new McpVersionStorageContents(serverBytes(streamable), null, null));
        
        service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, VERSION_ONE);
        
        verify(endpointOperationService).injectEndpoint(any(McpServerDetailInfo.class));
    }
    
    @Test
    void testCreateGeneratesIdAndRejectsDuplicateNameIdAndInvalidNamespace() throws Exception {
        McpServerBasicInfo generated = specification(VERSION_ONE, "generated");
        
        String generatedId = service.createMcpServer(null, generated, null, null, null);
        
        assertTrue(com.alibaba.nacos.common.utils.StringUtils.isUuidString(generatedId));
        
        when(mcpServerIndex.getMcpServerByName(NAMESPACE_ID, MCP_NAME))
            .thenReturn(McpServerIndexData.newIndexData(MCP_ID, NAMESPACE_ID));
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID,
                specification(VERSION_ONE, "duplicate-name"), null, null, null));
        when(mcpServerIndex.getMcpServerByName(NAMESPACE_ID, MCP_NAME)).thenReturn(null);
        when(mcpServerIndex.getMcpServerById(MCP_ID))
            .thenReturn(McpServerIndexData.newIndexData(MCP_ID, NAMESPACE_ID));
        McpServerBasicInfo duplicateId = specification(VERSION_ONE, "duplicate-id");
        duplicateId.setId(MCP_ID);
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, duplicateId, null, null, null));
        assertThrows(NacosException.class,
            () -> service.createMcpServer("invalid namespace",
                specification(VERSION_ONE, "invalid-namespace"), null, null, null));
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, null, null, null, null));
    }
    
    @Test
    void testPublishNewVersionWithoutExistingContentUpdatesPresentation() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION_ONE);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        when(mcpServerIndex.getMcpServerByName(NAMESPACE_ID, MCP_NAME))
            .thenReturn(McpServerIndexData.newIndexData(MCP_ID, NAMESPACE_ID));
        when(versionStorageService.loadIfPresent(any())).thenReturn(null);
        McpServerBasicInfo second = specification(VERSION_TWO, "published");
        second.setEnabled(false);
        second.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        
        service.updateMcpServer(NAMESPACE_ID, true, second, null, null, null, false);
        
        ArgumentCaptor<McpServerVersionInfo> published =
            ArgumentCaptor.forClass(McpServerVersionInfo.class);
        verify(manifestStorage).publish(eq(NAMESPACE_ID), published.capture());
        assertEquals(VERSION_TWO, published.getValue().getLatestPublishedVersion());
        assertEquals("published", published.getValue().getDescription());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE,
            published.getValue().getProtocol());
    }
    
    @Test
    void testDeleteMissingVersionAndLifecycleReconcileFailureRemainCompatible() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION_ONE);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "reconcile failed"))
            .when(reconciler).reconcileAfterLegacyDelete(NAMESPACE_ID, MCP_NAME, MCP_ID,
                manifest);
        
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, VERSION_TWO);
        
        verify(manifestStorage).publish(NAMESPACE_ID, manifest);
        assertEquals(VERSION_ONE, manifest.getLatestPublishedVersion());
        verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME);
    }
    
    @Test
    void testResolveIdentityAndManifestNotFoundPaths() throws Exception {
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
        
        when(mcpServerIndex.getMcpServerByName(NAMESPACE_ID, MCP_NAME))
            .thenReturn(McpServerIndexData.newIndexData(MCP_ID, NAMESPACE_ID));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testDetailRejectsBlankVersionInvalidJsonAndStoredIdentity() throws Exception {
        McpServerVersionInfo noLatest = manifest();
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(noLatest);
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, null));
        
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest(VERSION_ONE));
        when(versionStorageService.loadIfPresent(any())).thenReturn(
            new McpVersionStorageContents(bytes("invalid-json"), null, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, VERSION_ONE));
        
        McpServerStorageInfo wrongId = server(VERSION_ONE, false, false);
        wrongId.setId("9d7939c0-72ea-4ef4-b232-418d1e16b45c");
        when(versionStorageService.loadIfPresent(any())).thenReturn(
            new McpVersionStorageContents(serverBytes(wrongId), null, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME, VERSION_ONE));
    }
    
    private void stubVersionContent(String version, boolean tools, boolean resources)
        throws Exception {
        McpServerStorageInfo server = server(version, tools, resources);
        when(versionStorageService.loadIfPresent(any())).thenReturn(
            new McpVersionStorageContents(serverBytes(server), null, null));
    }
    
    private McpServerStorageInfo server(String version, boolean tools, boolean resources) {
        McpServerStorageInfo result = new McpServerStorageInfo();
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription("description-" + version);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setVersion(version);
        ServerVersionDetail detail = new ServerVersionDetail();
        detail.setVersion(version);
        result.setVersionDetail(detail);
        if (tools) {
            result.setToolsDescriptionRef(MCP_ID + '-' + version + "-mcp-tools.json");
        }
        if (resources) {
            result.setResourceDescriptionRef(MCP_ID + '-' + version + "-mcp-resources.json");
        }
        return result;
    }
    
    private McpServerBasicInfo specification(String version, String description) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(MCP_NAME);
        result.setDescription(description);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setVersion(version);
        return result;
    }
    
    private McpServerVersionInfo manifest(String... versions) {
        McpServerVersionInfo result = new McpServerVersionInfo();
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription("manifest");
        result.setEnabled(true);
        List<ServerVersionDetail> details = new ArrayList<>();
        for (String version : versions) {
            ServerVersionDetail detail = new ServerVersionDetail();
            detail.setVersion(version);
            details.add(detail);
        }
        result.setVersions(details);
        if (versions.length > 0) {
            result.setLatestPublishedVersion(versions[versions.length - 1]);
        }
        return result;
    }
    
    private byte[] serverBytes(McpServerStorageInfo server) {
        return bytes(JacksonUtils.toJson(server));
    }
    
    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
