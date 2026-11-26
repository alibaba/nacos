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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityModeResolver;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.sql.Timestamp;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.service.NamespaceOperationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Fixed shared MCP content, ordinary Endpoint projection, and governance isolation. */
class HubMcpReadAdapterTest {
    
    private final HubResourcePublicationService publications =
        mock(HubResourcePublicationService.class);
    private final HubResourceDirectoryService directory = mock(HubResourceDirectoryService.class);
    private final HubResourceDiscoveryProjector projector =
        mock(HubResourceDiscoveryProjector.class);
    private final McpLifecycleOperationService lifecycle = mock(McpLifecycleOperationService.class);
    private final McpEndpointOperationService endpoints = mock(McpEndpointOperationService.class);
    private final McpCompatibilityModeResolver mode = mock(McpCompatibilityModeResolver.class);
    private final HubMcpReadAdapter adapter = new HubMcpReadAdapter(publications, directory,
        projector, lifecycle, endpoints, mode);
    private final AiResource source = new AiResource();
    private final AiResource hub = new AiResource();
    private HubResourceReference reference;
    
    @BeforeEach
    void setUp() throws Exception {
        reference = HubResourceReferenceJsonUtil.build("source", 101L, "same-name", 201L, "1.0.0");
        hub.setId(501L);
        lenient().when(mode.localMemberSupportsManagedLifecycle()).thenReturn(true);
        lenient().when(publications.inspectPinned(eq(HubResourceType.MCP), eq(501L), any(), any()))
            .thenAnswer(call -> {
                String selected = call.getArgument(2);
                if (selected != null && !"1.0.0".equals(selected)) {
                    throw new NacosException(404, "not the shared version");
                }
                HubResourcePublicationService.PinnedPublicationReader<?> reader =
                    call.getArgument(3);
                return reader.read(hub, source, reference);
            });
        lenient().when(lifecycle.projectSharedVersion(source, reference))
            .thenAnswer(call -> content());
    }
    
    @Test
    void managementListCarriesHubTimestampInsideItsExistingBasicInfoResponse() throws Exception {
        hub.setGmtModified(new Timestamp(1700000000000L));
        source.setGmtModified(new Timestamp(1800000000000L));
        Page<HubResourceDirectoryService.Entry> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        page.setPageItems(Collections.singletonList(
            new HubResourceDirectoryService.Entry(hub, source, reference)));
        when(directory.list(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
            .thenReturn(page);
        HubMcpReadAdapter withRealMetadata = new HubMcpReadAdapter(publications, directory,
            new HubResourceDiscoveryProjector(mock(NamespaceOperationService.class),
                mock(HubSkillReadPermissionGuard.class)),
            lifecycle, endpoints, mode);
        McpServerBasicInfo result =
            withRealMetadata.list(null, "blur", 1, 12).getPageItems().get(0);
        assertEquals("501", result.getName());
        assertEquals(1700000000000L, result.getHub().getUpdateTime());
        assertEquals("same-name", result.getHub().getResourceName());
    }
    
    @Test
    void canonicalDetailHidesSourceHistoryAndGovernance() throws Exception {
        McpServerVersionDetail result = adapter.version("501", null);
        assertEquals("__nacos_ai_hub__", result.getNamespaceId());
        assertEquals("501", result.getMcpName());
        assertEquals(Collections.singletonMap("latest", "1.0.0"), result.getLabels());
        assertEquals(1, result.getOnlineCount());
        assertNull(result.getEditingVersion());
        assertNull(result.getReviewingVersion());
        assertEquals("internal pipeline", result.getPublishPipelineInfo());
        assertFalse(result.isWritable());
        assertEquals("PUBLIC", result.getScope());
        assertNull(result.getServerSpecification().getId());
        assertEquals("501", result.getServerSpecification().getName());
        verifyNoInteractions(endpoints);
    }
    
    @Test
    void servingPreservesReadOnlyEndpointProjectionAndOnlyFixedVersion() throws Exception {
        doAnswer(call -> {
            McpServerDetailInfo result = call.getArgument(0);
            assertEquals("source", result.getNamespaceId());
            assertEquals("same-name", result.getName());
            result.setBackendEndpoints(Collections.emptyList());
            return null;
        }).when(endpoints).injectEndpoint(any());
        McpServerDetailInfo result = adapter.serving("501", "1.0.0");
        assertEquals("__nacos_ai_hub__", result.getNamespaceId());
        assertEquals("501", result.getId());
        assertEquals("501", result.getName());
        assertEquals(1, result.getAllVersions().size());
        assertEquals("1.0.0", result.getAllVersions().get(0).getVersion());
        assertEquals(Collections.emptyList(), result.getBackendEndpoints());
        verify(endpoints).injectEndpoint(result);
    }
    
    @Test
    void stdioDoesNotResolveNamingEndpoints() throws Exception {
        McpServerVersionDetail content = content();
        content.getServerSpecification().setProtocol("stdio");
        when(lifecycle.projectSharedVersion(source, reference)).thenReturn(content);
        adapter.serving("501", null);
        verifyNoInteractions(endpoints);
    }
    
    @Test
    void fixedVersionAndOpaqueIdentityRejectSourceCoordinatesBeforeStorage() {
        assertThrows(NacosException.class, () -> adapter.version("501", "2.0.0"));
        assertThrows(NacosException.class, () -> adapter.resolveId("source-uuid", "501"));
        assertThrows(NacosException.class, () -> adapter.resolveId(null, "same-name"));
        verifyNoInteractions(lifecycle, endpoints);
    }
    
    @Test
    void unsupportedMemberCannotBypassMcpLifecycleGate() {
        when(mode.localMemberSupportsManagedLifecycle()).thenReturn(false);
        assertThrows(NacosException.class, () -> adapter.serving("501", null));
        verifyNoInteractions(publications, lifecycle, endpoints);
    }
    
    @Test
    void versionPagingExposesNoSourceDraftsOrHistoricalVersions() throws Exception {
        assertEquals(1, adapter.versions("501", "online", 2, 10).getTotalCount());
        assertEquals(0, adapter.versions("501", "online", 2, 10).getPageItems().size());
        assertEquals(0, adapter.versions("501", "draft", 1, 10).getTotalCount());
        assertThrows(NacosException.class, () -> adapter.versions("501", null, 0, 10));
    }
    
    private McpServerVersionDetail content() {
        McpServerVersionDetail detail = new McpServerVersionDetail();
        detail.setNamespaceId("source");
        detail.setMcpName("same-name");
        detail.setVersion("1.0.0");
        detail.setStatus("online");
        detail.setWritable(true);
        detail.setScope("PRIVATE");
        detail.setLabels(Collections.singletonMap("stable", "2.0.0"));
        detail.setEditingVersion("3.0.0");
        detail.setReviewingVersion("2.0.0");
        detail.setPublishPipelineInfo("internal pipeline");
        detail.setOnlineCount(2);
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setNamespaceId("source");
        server.setId("source-uuid");
        server.setName("same-name");
        server.setProtocol("sse");
        ServerVersionDetail version = new ServerVersionDetail();
        version.setVersion("1.0.0");
        version.setIs_latest(false);
        server.setVersionDetail(version);
        detail.setServerSpecification(server);
        return detail;
    }
}
