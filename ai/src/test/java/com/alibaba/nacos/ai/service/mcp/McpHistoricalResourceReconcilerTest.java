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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageKeyComposer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpHistoricalResourceReconcilerTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final String MCP_NAME = "demo";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private McpVersionStorageService versionStorageService;
    
    @Mock
    private AiResourcePersistService resourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService versionPersistService;
    
    private McpHistoricalResourceReconciler reconciler;
    
    @BeforeEach
    void setUp() {
        reconciler = new McpHistoricalResourceReconciler(versionStorageService,
            resourcePersistService, versionPersistService);
    }
    
    @Test
    void testReconcileCreatesVersionsBeforeResourceWithoutRewritingPayload() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION, "2.0.0");
        stubStorage(VERSION, "2.0.0");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.emptyList(), 0, 0));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.emptyList(), 0, 0));
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(1L);
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(1L);
        
        assertEquals(3, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        ArgumentCaptor<AiResourceVersion> versionCaptor =
            ArgumentCaptor.forClass(AiResourceVersion.class);
        verify(versionPersistService, org.mockito.Mockito.times(2))
            .insert(versionCaptor.capture());
        assertEquals(Arrays.asList(VERSION, "2.0.0"),
            versionCaptor.getAllValues().stream().map(AiResourceVersion::getVersion).toList());
        for (AiResourceVersion row : versionCaptor.getAllValues()) {
            assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, row.getStatus());
            assertEquals("nacos", row.getAuthor());
            assertEquals("", row.getDesc());
            McpVersionStorageDescriptor descriptor =
                McpVersionStorageDescriptorSerializer.deserialize(row.getStorage());
            assertTrue(descriptor.getServerKey().contains(MCP_ID));
            assertNotNull(descriptor.getToolKey());
            assertNotNull(descriptor.getResourceKey());
        }
        ArgumentCaptor<AiResource> resourceCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(resourcePersistService).insert(resourceCaptor.capture());
        assertResource(resourceCaptor.getValue(), manifest, 2);
        verify(versionStorageService, never()).save(any(), any());
        verify(versionStorageService, never()).delete(any());
    }
    
    @Test
    void testReconcileIsZeroDifferenceForEquivalentRowsAndSemanticJson() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        AiResource resource = expectedResource(manifest, 1);
        resource.setBizTags(" [ ] ");
        ResourceVersionInfo versionInfo = JacksonUtils.toObj(resource.getVersionInfo(),
            ResourceVersionInfo.class);
        resource.setVersionInfo(JacksonUtils.toJson(versionInfo));
        AiResourceVersion version = expectedVersion(VERSION);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(resource), 1, 1));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(version), 1, 1));
        
        assertEquals(0, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        verify(versionPersistService, never()).insert(any());
        verify(resourcePersistService, never()).insert(any());
        verify(resourcePersistService, never()).updateMetaCas(any(), any(), any(),
            org.mockito.ArgumentMatchers.anyLong(), any());
    }
    
    @Test
    void testReconcileRepairsMutableLegacyMetadataWithCas() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        manifest.setEnabled(false);
        stubStorage(VERSION);
        AiResource stale = expectedResource(manifest, 1);
        stale.setMetaVersion(7L);
        stale.setDesc("stale");
        stale.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        stale.setBizTags(null);
        stale.setVersionInfo("not-json");
        stubRows(stale, Collections.singletonList(expectedVersion(VERSION)));
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(MCP_NAME),
            eq(AiResourceConstants.RESOURCE_TYPE_MCP), eq(7L), any(AiResource.class)))
            .thenReturn(true);
        
        assertEquals(1, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(MCP_NAME),
            eq(AiResourceConstants.RESOURCE_TYPE_MCP), eq(7L), captor.capture());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, captor.getValue().getStatus());
        assertEquals("legacy description", captor.getValue().getDesc());
    }
    
    @Test
    void testReconcileRepairsMalformedMutableMetadata() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        AiResource stale = expectedResource(manifest, 1);
        stale.setMetaVersion(7L);
        stale.setBizTags("not-json");
        stubRows(stale, Collections.singletonList(expectedVersion(VERSION)));
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(MCP_NAME),
            eq(AiResourceConstants.RESOURCE_TYPE_MCP), eq(7L), any(AiResource.class)))
            .thenReturn(true);
        
        assertEquals(1, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(resourcePersistService);
        stale = expectedResource(manifest, 1);
        stale.setMetaVersion(8L);
        stale.setVersionInfo("not-json");
        stubRows(stale, Collections.singletonList(expectedVersion(VERSION)));
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(MCP_NAME),
            eq(AiResourceConstants.RESOURCE_TYPE_MCP), eq(8L), any(AiResource.class)))
            .thenReturn(true);
        
        assertEquals(1, reconciler.reconcile(NAMESPACE_ID, manifest));
    }
    
    @Test
    void testReconcileRecoversConcurrentResourceCasAndInsert() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        AiResource expected = expectedResource(manifest, 1);
        AiResource stale = expectedResource(manifest, 1);
        stale.setMetaVersion(2L);
        stale.setDesc("stale");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(stale), 1, 1),
                page(Collections.singletonList(expected), 1, 1));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(expectedVersion(VERSION)), 1, 1));
        when(resourcePersistService.updateMetaCas(any(), any(), any(), eq(2L), any()))
            .thenReturn(false);
        assertEquals(0, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(resourcePersistService);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.emptyList(), 0, 0),
                page(Collections.singletonList(expected), 1, 1));
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new IllegalStateException("race"));
        assertEquals(0, reconciler.reconcile(NAMESPACE_ID, manifest));
    }
    
    @Test
    void testReconcileRejectsConcurrentResourceConflicts() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        AiResource stale = expectedResource(manifest, 1);
        stale.setMetaVersion(2L);
        stale.setDesc("stale");
        AiResource conflicting = expectedResource(manifest, 1);
        conflicting.setDesc("conflict");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(stale), 1, 1),
                page(Collections.singletonList(conflicting), 1, 1));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(expectedVersion(VERSION)), 1, 1));
        when(resourcePersistService.updateMetaCas(any(), any(), any(), eq(2L), any()))
            .thenReturn(false);
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(resourcePersistService);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.emptyList(), 0, 0),
                page(Collections.singletonList(conflicting), 1, 1));
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new IllegalStateException("race"));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(resourcePersistService);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.emptyList(), 0, 0), page(Collections.emptyList(), 0, 0));
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new IllegalStateException("failed"));
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
        
        reset(resourcePersistService);
        AiResource duplicate = expectedResource(manifest, 1);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(stale), 1, 1),
                page(Arrays.asList(conflicting, duplicate), 2, 1));
        when(resourcePersistService.updateMetaCas(any(), any(), any(), eq(2L), any()))
            .thenReturn(false);
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
    }
    
    @Test
    void testReconcileRecoversConcurrentVersionInsertAndRejectsConflicts() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.emptyList(), 0, 0));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.emptyList(), 0, 0));
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new IllegalStateException("race"));
        when(versionPersistService.find(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION)).thenReturn(expectedVersion(VERSION));
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(1L);
        assertEquals(1, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionPersistService, resourcePersistService);
        stubEmptyResource();
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.emptyList(), 0, 0));
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new IllegalStateException("race"));
        AiResourceVersion conflict = expectedVersion(VERSION);
        conflict.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(versionPersistService.find(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION)).thenReturn(conflict);
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
        
        when(versionPersistService.find(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION)).thenReturn(null);
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
    }
    
    @Test
    void testReconcileRejectsDuplicateOrIndependentResourceRows() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        AiResource first = expectedResource(manifest, 1);
        AiResource second = expectedResource(manifest, 1);
        second.setFrom("local");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Arrays.asList(first, second), 2, 1));
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
        verifyNoInteractions(versionPersistService);
        
        reset(resourcePersistService);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Arrays.asList(first, second), 3, 2));
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
        verifyNoInteractions(versionPersistService);
        
        reset(resourcePersistService);
        first.setFrom("local");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(first), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        verifyNoInteractions(versionPersistService);
    }
    
    @Test
    void testReconcileRejectsInvalidResourceQueryAndMetadataIdentity() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(null);
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
        
        AiResource inconsistent = expectedResource(manifest, 1);
        inconsistent.setNamespaceId("other");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(inconsistent), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        AiResource badExt = expectedResource(manifest, 1);
        badExt.setExt("{}");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(badExt), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        AiResource noMetaVersion = expectedResource(manifest, 1);
        noMetaVersion.setMetaVersion(null);
        noMetaVersion.setDesc("stale");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(noMetaVersion), 1, 1));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(expectedVersion(VERSION)), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
    }
    
    @Test
    void testReconcileRejectsExtraOrConflictingVersionRows() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        AiResource resource = expectedResource(manifest, 1);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(resource), 1, 1));
        AiResourceVersion expected = expectedVersion(VERSION);
        AiResourceVersion extra = expectedVersion("0.9.0");
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Arrays.asList(expected, extra), 2, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        verify(versionPersistService, never()).delete(any(), any(), any(), any());
        
        expected.setStorage("{}");
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(expected), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        expected = expectedVersion(VERSION);
        expected.setPublishPipelineInfo("{}");
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(expected), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
    }
    
    @Test
    void testReconcilePagesVersionsAndRejectsInconsistentPages() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        stubStorage(VERSION);
        stubEmptyResource();
        Page<AiResourceVersion> firstPage = page(Collections.emptyList(), 101, 0);
        Page<AiResourceVersion> secondPage = page(Collections.emptyList(), 101, 0);
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100)).thenReturn(firstPage);
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 2, 100)).thenReturn(secondPage);
        when(versionPersistService.insert(any())).thenReturn(1L);
        when(resourcePersistService.insert(any())).thenReturn(1L);
        assertEquals(2, reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionPersistService, resourcePersistService);
        stubEmptyResource();
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100)).thenReturn(null);
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
        
        AiResourceVersion inconsistent = expectedVersion(VERSION);
        inconsistent.setName("other");
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(Collections.singletonList(inconsistent), 1, 1));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
    }
    
    @Test
    void testReconcileRejectsInvalidManifestShapesBeforeStorageAccess() {
        assertInvalidManifest(null);
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile("invalid namespace", manifest(VERSION))).getErrCode());
        
        McpServerVersionInfo invalid = manifest(VERSION);
        invalid.setName(" ");
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.setName("x".repeat(257));
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.setDescription("x".repeat(2049));
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.setVersions(null);
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.setVersions(Collections.emptyList());
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.setVersions(Collections.singletonList(null));
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.getVersionDetails().get(0).setVersion(" ");
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.getVersionDetails().get(0).setVersion("x".repeat(65));
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION, VERSION);
        assertInvalidManifest(invalid);
        invalid = manifest(VERSION);
        invalid.setLatestPublishedVersion("other");
        assertInvalidManifest(invalid);
        verifyNoInteractions(versionStorageService, resourcePersistService,
            versionPersistService);
    }
    
    @Test
    void testReconcileValidatesServerToolsAndResourcesContent() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        when(versionStorageService.load(any())).thenReturn(
            new McpVersionStorageContents(bytes("not-json"), null, null));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        when(versionStorageService.load(any())).thenReturn(
            new McpVersionStorageContents(bytes("null"), null, null));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        McpServerStorageInfo wrongId = server(VERSION);
        wrongId.setId("11111111-1111-1111-1111-111111111111");
        stubContents(wrongId, wrongId, bytes("{}"), bytes("{}"));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        McpServerStorageInfo conflictingVersion = server(VERSION);
        conflictingVersion.setVersion("2.0.0");
        stubContents(conflictingVersion, conflictingVersion, bytes("{}"), bytes("{}"));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        McpServerStorageInfo changed = server(VERSION);
        McpServerStorageInfo changedFull = server(VERSION);
        changedFull.setDescription("changed");
        stubContents(changed, changedFull, bytes("{}"), bytes("{}"));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        stubContents(server(VERSION), server(VERSION), bytes("not-json"), bytes("{}"));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        stubContents(server(VERSION), server(VERSION), bytes("{}"), bytes("not-json"));
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        reset(versionStorageService);
        McpServerStorageInfo legacyVersion = server(VERSION);
        legacyVersion.setVersionDetail(null);
        legacyVersion.setVersion(VERSION);
        stubContents(legacyVersion, legacyVersion, null, null);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(null);
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
    }
    
    @Test
    void testReconcilePropagatesMissingStorageContentWithoutDatabaseWrites() throws Exception {
        McpServerVersionInfo manifest = manifest(VERSION);
        when(versionStorageService.load(any())).thenThrow(
            new NacosException(NacosException.SERVER_ERROR, "missing"));
        
        assertThrows(NacosException.class, () -> reconciler.reconcile(NAMESPACE_ID, manifest));
        
        verifyNoInteractions(resourcePersistService, versionPersistService);
    }
    
    private void assertInvalidManifest(McpServerVersionInfo manifest) {
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> reconciler.reconcile(NAMESPACE_ID, manifest)).getErrCode());
    }
    
    private void stubRows(AiResource resource, List<AiResourceVersion> versions) {
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.singletonList(resource), 1, 1));
        when(versionPersistService.list(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, 1, 100))
            .thenReturn(page(versions, versions.size(), 1));
    }
    
    private void stubEmptyResource() {
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(Collections.emptyList(), 0, 0));
    }
    
    private void stubStorage(String... versions) throws Exception {
        Map<String, McpServerStorageInfo> servers = new LinkedHashMap<>();
        for (String version : versions) {
            McpServerStorageInfo server = server(version);
            McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.fromLegacy(
                NAMESPACE_ID, MCP_ID, version, server);
            servers.put(descriptor.getServerKey(), server);
        }
        when(versionStorageService.load(any())).thenAnswer(invocation -> {
            McpVersionStorageDescriptor descriptor = invocation.getArgument(0);
            McpServerStorageInfo server = servers.get(descriptor.getServerKey());
            byte[] serverContent = bytes(JacksonUtils.toJson(server));
            return descriptor.getToolKey() == null
                ? new McpVersionStorageContents(serverContent, null, null)
                : new McpVersionStorageContents(serverContent, bytes("{}"), bytes("{}"));
        });
    }
    
    private void stubContents(McpServerStorageInfo firstServer,
        McpServerStorageInfo fullServer, byte[] tools, byte[] resources) throws Exception {
        when(versionStorageService.load(any())).thenReturn(
            new McpVersionStorageContents(bytes(JacksonUtils.toJson(firstServer)), null, null),
            new McpVersionStorageContents(bytes(JacksonUtils.toJson(fullServer)), tools,
                resources));
    }
    
    private McpServerStorageInfo server(String version) {
        McpServerStorageInfo result = new McpServerStorageInfo();
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription("legacy description");
        ServerVersionDetail detail = new ServerVersionDetail();
        detail.setVersion(version);
        result.setVersionDetail(detail);
        result.setToolsDescriptionRef(McpConfigUtils.formatServerToolSpecDataId(MCP_ID, version));
        result.setResourceDescriptionRef(
            McpConfigUtils.formatServerResourceSpecDataId(MCP_ID, version));
        return result;
    }
    
    private McpServerVersionInfo manifest(String... versions) {
        McpServerVersionInfo result = new McpServerVersionInfo();
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription("legacy description");
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
    
    private AiResource expectedResource(McpServerVersionInfo manifest, int onlineCount) {
        McpResourceExt ext = new McpResourceExt();
        ext.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        ext.setMcpId(MCP_ID);
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setOnlineCnt(onlineCount);
        versionInfo.setLabels(Collections.singletonMap(AiResourceConstants.LABEL_LATEST,
            manifest.getLatestPublishedVersion()));
        AiResource result = new AiResource();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(MCP_NAME);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setDesc(manifest.getDescription());
        result.setStatus(manifest.isEnabled() ? AiResourceConstants.META_STATUS_ENABLE
            : AiResourceConstants.META_STATUS_DISABLE);
        result.setBizTags("[]");
        result.setExt(McpResourceExtSerializer.serialize(ext));
        result.setFrom(McpHistoricalResourceReconciler.LEGACY_SOURCE);
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        result.setMetaVersion(1L);
        result.setScope(VisibilityConstants.SCOPE_PUBLIC);
        result.setOwner("nacos");
        return result;
    }
    
    private AiResourceVersion expectedVersion(String version) {
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.fromLegacy(
            NAMESPACE_ID, MCP_ID, version, server(version));
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(MCP_NAME);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setVersion(version);
        result.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        result.setAuthor("nacos");
        result.setDesc("");
        result.setStorage(McpVersionStorageDescriptorSerializer.serialize(descriptor));
        return result;
    }
    
    private void assertResource(AiResource resource, McpServerVersionInfo manifest,
        int onlineCount) {
        assertEquals(NAMESPACE_ID, resource.getNamespaceId());
        assertEquals(MCP_NAME, resource.getName());
        assertEquals(AiResourceConstants.RESOURCE_TYPE_MCP, resource.getType());
        assertEquals(McpHistoricalResourceReconciler.LEGACY_SOURCE, resource.getFrom());
        assertEquals("nacos", resource.getOwner());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, resource.getScope());
        assertEquals(MCP_ID, McpResourceExtSerializer.deserialize(resource.getExt()).getMcpId());
        ResourceVersionInfo versionInfo = JacksonUtils.toObj(resource.getVersionInfo(),
            ResourceVersionInfo.class);
        assertEquals(onlineCount, versionInfo.getOnlineCnt());
        assertEquals(manifest.getLatestPublishedVersion(),
            versionInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
    
    private <T> Page<T> page(List<T> items, int totalCount, int pagesAvailable) {
        Page<T> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(totalCount);
        result.setPagesAvailable(pagesAvailable);
        return result;
    }
}
