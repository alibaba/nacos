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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiResourceIndexServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiResourceIndexServiceImplTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private AiResourceSearchRepository repository;
    
    @Mock
    private AiResourceEmbeddingService embeddingService;
    
    @Mock
    private AiResourceVectorIndex vectorIndex;
    
    @Mock
    private AiResourceIndexEnhancementService enhancementService;
    
    @Mock
    private AiResourceIndexContentLoader contentLoader;
    
    @Mock
    private McpLifecycleOperationService mcpServerOperationService;
    
    @Test
    void rebuildAiResourceShouldPersistEntryChunksAndVectors() throws Exception {
        AiResourceIndexServiceImpl service = service();
        AiResource meta = meta();
        AiResourceVersion version = version(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta);
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(version);
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.embed(any())).thenReturn(new double[] {1.0D});
        when(repository.replaceEntry(any(AiResourceSearchDocument.class), anyList()))
            .thenAnswer(invocation -> {
                AiResourceSearchDocument entry = invocation.getArgument(0);
                entry.setId(10L);
                List<AiResourceSearchChunk> chunks = invocation.getArgument(1);
                long id = 1L;
                for (AiResourceSearchChunk chunk : chunks) {
                    chunk.setId(id++);
                }
                return chunks;
            });
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            "1.0.0");
        
        ArgumentCaptor<AiResourceSearchDocument> entryCaptor =
            ArgumentCaptor.forClass(AiResourceSearchDocument.class);
        ArgumentCaptor<List<AiResourceSearchChunk>> chunksCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(repository).replaceEntry(entryCaptor.capture(), chunksCaptor.capture());
        AiResourceSearchDocument entry = entryCaptor.getValue();
        assertEquals("public", entry.getNamespaceId());
        assertEquals(AiResourceConstants.RESOURCE_TYPE_SKILL, entry.getResourceType());
        assertEquals("api-helper", entry.getResourceName());
        assertFalse(chunksCaptor.getValue().isEmpty());
        ArgumentCaptor<Collection<AiResourceVectorDocument>> vectorCaptor =
            ArgumentCaptor.forClass(Collection.class);
        verify(vectorIndex).replaceResourceVersion(eq("public"),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("api-helper"), eq("1.0.0"),
            vectorCaptor.capture());
        assertFalse(vectorCaptor.getValue().isEmpty());
        verify(repository).updateEntryStatus(10L, AiResourceSearchConstants.STATUS_ENABLED);
        assertEquals(AiResourceSearchConstants.STATUS_PENDING, entry.getStatus());
    }
    
    @Test
    void vectorFailureShouldLeaveRelationalEntryPendingForRetry() throws Exception {
        AiResourceIndexServiceImpl service = service();
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.embed(any())).thenReturn(new double[] {1.0D});
        when(repository.replaceEntry(any(AiResourceSearchDocument.class), anyList()))
            .thenAnswer(invocation -> {
                AiResourceSearchDocument entry = invocation.getArgument(0);
                entry.setId(10L);
                List<AiResourceSearchChunk> chunks = invocation.getArgument(1);
                long id = 1L;
                for (AiResourceSearchChunk chunk : chunks) {
                    chunk.setId(id++);
                }
                return chunks;
            });
        doThrow(new IllegalStateException("pgvector unavailable")).when(vectorIndex)
            .replaceResourceVersion(eq("public"), eq(Constants.Skills.RESOURCE_TYPE_SKILL),
                eq("api-helper"), eq("1.0.0"), anyList());
        
        assertThrows(IllegalStateException.class,
            () -> service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL,
                "api-helper", "1.0.0"));
        
        ArgumentCaptor<AiResourceSearchDocument> entryCaptor =
            ArgumentCaptor.forClass(AiResourceSearchDocument.class);
        verify(repository).replaceEntry(entryCaptor.capture(), anyList());
        assertEquals(AiResourceSearchConstants.STATUS_PENDING, entryCaptor.getValue().getStatus());
        verify(repository, never()).updateEntryStatus(anyLong(), anyString());
    }
    
    @Test
    void rebuildAiResourceShouldPersistSkillContentChunksWithoutLlm() throws Exception {
        final AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(contentLoader.load(any(AiResourceSearchDocument.class), any(AiResourceVersion.class)))
            .thenReturn(
                List.of(new AiResourceIndexEnhancementContent("SKILL.md",
                    "---\ndescription: Create avatar videos.\n---\n## Triggers\n- talking head")));
        when(repository.replaceEntry(any(AiResourceSearchDocument.class), anyList()))
            .thenAnswer(invocation -> {
                AiResourceSearchDocument entry = invocation.getArgument(0);
                entry.setId(10L);
                List<AiResourceSearchChunk> chunks = invocation.getArgument(1);
                long id = 1L;
                for (AiResourceSearchChunk chunk : chunks) {
                    chunk.setId(id++);
                }
                return chunks;
            });
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            "1.0.0");
        
        ArgumentCaptor<List<AiResourceSearchChunk>> chunksCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(repository).replaceEntry(any(AiResourceSearchDocument.class),
            chunksCaptor.capture());
        assertTrue(chunksCaptor.getValue().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SKILL_CONTENT.equals(chunk.getChunkType())
                && chunk.getChunkText().contains("talking head")));
        verify(enhancementService, never()).ready();
        verify(enhancementService, never()).enhance(any(AiResourceSearchDocument.class), anyList(),
            anyList());
    }
    
    @Test
    void rebuildLatestAiResourceShouldDeleteExistingResourceIndexBeforePersistLatest()
        throws Exception {
        AiResourceIndexServiceImpl service = service();
        AiResource meta = meta();
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta);
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.embed(any())).thenReturn(new double[] {1.0D});
        when(repository.replaceEntry(any(AiResourceSearchDocument.class), anyList()))
            .thenAnswer(invocation -> {
                AiResourceSearchDocument entry = invocation.getArgument(0);
                entry.setId(10L);
                List<AiResourceSearchChunk> chunks = invocation.getArgument(1);
                long id = 1L;
                for (AiResourceSearchChunk chunk : chunks) {
                    chunk.setId(id++);
                }
                return chunks;
            });
        
        service.rebuildLatestAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "api-helper");
        
        verify(repository).deleteByResource("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "api-helper");
        verify(vectorIndex).deleteByResource("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "api-helper");
        verify(vectorIndex).replaceResourceVersion(eq("public"),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("api-helper"), eq("1.0.0"),
            anyList());
    }
    
    @Test
    void rebuildAiResourceWithoutVersionShouldRemoveMissingLatestProjection() throws Exception {
        AiResourceIndexServiceImpl service = service();
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "missing", null);
        
        verify(repository).deleteByResource("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "missing");
        verify(repository, never()).deleteByResourceVersion(any(), any(), any(), any());
        verify(repository, never()).replaceEntry(any(), anyList());
    }
    
    @Test
    void enhanceLatestAiResourceShouldReplaceEnhancedChunksAndFullVectorIndex() throws Exception {
        final AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        AiResource meta = meta();
        AiResourceVersion version = version(AiResourceConstants.VERSION_STATUS_ONLINE);
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setId(10L);
        entry.setNamespaceId("public");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName("api-helper");
        entry.setResourceVersion("1.0.0");
        when(repository.findEntry("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"))
            .thenReturn(entry);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta);
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version);
        when(enhancementService.ready()).thenReturn(true);
        when(contentLoader.load(any(AiResourceSearchDocument.class), any(AiResourceVersion.class)))
            .thenReturn(
                List.of(new AiResourceIndexEnhancementContent("SKILL.md",
                    "Create talking avatar videos")));
        AiResourceSearchChunk baseChunk = new AiResourceSearchChunk();
        baseChunk.setId(1L);
        baseChunk.setDocumentId(10L);
        baseChunk.setChunkType(AiResourceSearchConstants.CHUNK_TYPE_DESCRIPTION);
        baseChunk.setCanonicalText("skill api helper description");
        when(repository.listChunks(10L)).thenReturn(List.of(baseChunk));
        when(enhancementService.enhanceWithResult(any(AiResourceSearchDocument.class), anyList(),
            anyList())).thenReturn(new AiResourceIndexEnhancementResult(
                List.of(
                    new AiResourceIndexEnhancementChunk(
                        AiResourceSearchConstants.CHUNK_TYPE_DESCRIPTION,
                        "must not persist as an enhancement", null),
                    new AiResourceIndexEnhancementChunk(
                        AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT,
                        "参数表格 parameter table", "{\"source\":\"llm\"}")),
                "fingerprint-v1"));
        when(repository.replaceEnhancementChunks(any(AiResourceSearchDocument.class), anyList()))
            .thenAnswer(invocation -> {
                List<AiResourceSearchChunk> chunks = invocation.getArgument(1);
                long id = 100L;
                for (AiResourceSearchChunk chunk : chunks) {
                    chunk.setDocumentId(10L);
                    chunk.setId(id++);
                }
                return List.of(baseChunk, chunks.get(0));
            });
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.embed(any())).thenReturn(new double[] {1.0D});
        
        assertTrue(service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"));
        
        ArgumentCaptor<List<AiResourceSearchChunk>> chunksCaptor =
            ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<AiResourceIndexEnhancementContent>> contentCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(enhancementService).enhanceWithResult(any(AiResourceSearchDocument.class), anyList(),
            contentCaptor.capture());
        assertEquals("SKILL.md", contentCaptor.getValue().get(0).getPath());
        verify(repository).replaceEnhancementChunks(any(AiResourceSearchDocument.class),
            chunksCaptor.capture());
        assertTrue(chunksCaptor.getValue().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT
                .equals(chunk.getChunkType())));
        verify(repository).updateEntryStatus(10L, AiResourceSearchConstants.STATUS_PENDING);
        verify(vectorIndex).replaceResourceVersion(eq("public"),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("api-helper"), eq("1.0.0"), anyList());
        verify(repository).updateEntryStatus(10L, AiResourceSearchConstants.STATUS_ENABLED);
        verify(vectorIndex, never()).addDocuments(anyList());
    }
    
    @Test
    void enhanceLatestAiResourceShouldRequestBaseRebuildWhenEntryIsStale() throws Exception {
        AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setResourceVersion("0.9.0");
        when(repository.findEntry("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"))
            .thenReturn(entry);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        
        assertFalse(service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"));
        
        verify(enhancementService, never()).enhanceWithResult(any(), anyList(), anyList());
    }
    
    @Test
    void enhanceLatestAiResourceShouldIgnoreMissingIndexEntry() throws Exception {
        AiResourceIndexServiceImpl service = service();
        
        assertFalse(service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "missing"));
        
        verify(resourceManager, never()).findMeta(any(), any(), any());
    }
    
    @Test
    void enhancementShouldNotPersistAfterTaskOwnershipIsLost() throws Exception {
        AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setId(10L);
        entry.setResourceVersion("1.0.0");
        when(repository.findEntry("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"))
            .thenReturn(entry);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(enhancementService.ready()).thenReturn(true);
        AtomicBoolean owned = new AtomicBoolean(true);
        when(enhancementService.enhanceWithResult(any(), anyList(), anyList()))
            .thenAnswer(invocation -> {
                owned.set(false);
                return new AiResourceIndexEnhancementResult(List.of(), "fingerprint-v1");
            });
        
        String result = service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper", owned::get);
        
        assertNull(result);
        verify(repository, never()).replaceEnhancementChunks(any(), anyList());
        verify(vectorIndex, never()).replaceResourceVersion(any(), any(), any(), any(), anyList());
    }
    
    @Test
    void enhancementShouldStopAtEachLaterOwnershipBoundary() throws Exception {
        AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        AiResourceSearchDocument entry = enhancementEntry();
        prepareEnhancement(entry);
        when(vectorIndex.available()).thenReturn(true);
        AtomicInteger beforePersistenceChecks = new AtomicInteger();
        String beforePersistence = service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            () -> beforePersistenceChecks.incrementAndGet() == 1);
        assertNull(beforePersistence);
        verify(repository, never()).replaceEnhancementChunks(any(), anyList());
        
        org.mockito.Mockito.reset(repository, resourceManager, enhancementService, contentLoader,
            vectorIndex);
        entry = enhancementEntry();
        prepareEnhancement(entry);
        when(vectorIndex.available()).thenReturn(true);
        when(repository.replaceEnhancementChunks(entry, Collections.emptyList()))
            .thenReturn(Collections.emptyList());
        AtomicInteger beforeVectorChecks = new AtomicInteger();
        String beforeVector = service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            () -> beforeVectorChecks.incrementAndGet() <= 2);
        assertNull(beforeVector);
        verify(repository).replaceEnhancementChunks(entry, Collections.emptyList());
        verify(vectorIndex, never()).replaceResourceVersion(any(), any(), any(), any(), anyList());
    }
    
    @Test
    void enhancementShouldRejectUnavailableService() {
        AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        AiResourceSearchDocument entry = enhancementEntry();
        when(repository.findEntry("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"))
            .thenReturn(entry);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(enhancementService.ready()).thenReturn(false);
        
        assertThrows(IllegalStateException.class, () -> service.enhanceLatestAiResource("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"));
    }
    
    @Test
    void missingHandlerAndNoopEnhancementShouldUseSafeFallbacks() throws Exception {
        AiResourceSearchTypeHandlerRegistry emptyRegistry =
            new AiResourceSearchTypeHandlerRegistry(Collections.emptyList());
        AiResourceIndexServiceImpl service = new AiResourceIndexServiceImpl(repository,
            embeddingService, vectorIndex, null, emptyRegistry);
        
        assertFalse(service.isEnhancementRequested());
        assertEquals(AiResourceIndexEnhancementService.NOOP.fingerprint(),
            service.enhancementFingerprint());
        service.rebuildAiResource("public", "unknown", "missing", "1.0.0");
        assertFalse(service.rebuildLatestAiResource("public", "unknown", "missing"));
        AiResourceSearchDocument entry = enhancementEntry();
        when(repository.findEntry("public", "unknown", "indexed")).thenReturn(entry);
        assertFalse(service.enhanceLatestAiResource("public", "unknown", "indexed"));
        verify(repository).deleteByResourceVersion("public", "unknown", "missing", "1.0.0");
        verify(repository).deleteByResource("public", "unknown", "missing");
    }
    
    @Test
    void deletionShouldValidateKeysAndRemoveRelationalAndVectorState() {
        AiResourceIndexServiceImpl service = service();
        service.deleteResource("public", "skill", null);
        service.deleteResourceVersion("public", "skill", "name", null);
        verify(repository, never()).deleteByResource(any(), any(), any());
        verify(repository, never()).deleteByResourceVersion(any(), any(), any(), any());
        when(vectorIndex.available()).thenReturn(true);
        
        service.deleteResource("public", "skill", "name");
        service.deleteResourceVersion("public", "skill", "name", "1.0.0");
        
        verify(vectorIndex).deleteByResource("public", "skill", "name");
        verify(repository).deleteByResource("public", "skill", "name");
        verify(vectorIndex).deleteByResourceVersion("public", "skill", "name", "1.0.0");
        verify(repository).deleteByResourceVersion("public", "skill", "name", "1.0.0");
    }
    
    @Test
    void rebuildShouldAllowVectorIndexWithNoPersistedChunks() throws Exception {
        AiResourceIndexServiceImpl service = service();
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(vectorIndex.available()).thenReturn(true);
        when(repository.replaceEntry(any(), anyList())).thenAnswer(invocation -> {
            AiResourceSearchDocument entry = invocation.getArgument(0);
            entry.setId(1L);
            return Collections.emptyList();
        });
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            "1.0.0");
        
        verify(vectorIndex).replaceResourceVersion("public",
            Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper", "1.0.0",
            Collections.emptyList());
    }
    
    @Test
    void rebuildMcpServerShouldPersistToolContentChunks() throws Exception {
        AiResourceIndexServiceImpl service = service(enhancementService, contentLoader);
        McpServerDetailInfo mcpServer = mcpServer();
        when(repository.replaceEntry(any(AiResourceSearchDocument.class), anyList()))
            .thenAnswer(invocation -> {
                AiResourceSearchDocument entry = invocation.getArgument(0);
                entry.setId(10L);
                List<AiResourceSearchChunk> chunks = invocation.getArgument(1);
                long id = 1L;
                for (AiResourceSearchChunk chunk : chunks) {
                    chunk.setId(id++);
                }
                return chunks;
            });
        
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-avatar", null))
            .thenReturn(mcpServer);
        service.rebuildLatestAiResource("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "mcp-avatar");
        
        ArgumentCaptor<List<AiResourceSearchChunk>> chunksCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(repository).replaceEntry(any(AiResourceSearchDocument.class),
            chunksCaptor.capture());
        assertTrue(chunksCaptor.getValue().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_MCP_CONTENT.equals(chunk.getChunkType())
                && chunk.getChunkText().contains("avatar video")));
    }
    
    @Test
    void rebuildAiResourceShouldDeleteOfflineVersion() throws Exception {
        AiResourceIndexServiceImpl service = service();
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_DRAFT));
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            "1.0.0");
        
        verify(repository).deleteByResourceVersion("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "api-helper", "1.0.0");
        verify(repository, never()).replaceEntry(any(), anyList());
    }
    
    private AiResourceIndexServiceImpl service() {
        return service(AiResourceIndexEnhancementService.NOOP,
            AiResourceIndexContentLoader.NOOP);
    }
    
    private AiResourceIndexServiceImpl service(
        AiResourceIndexEnhancementService indexEnhancementService,
        AiResourceIndexContentLoader indexContentLoader) {
        AiResourceSearchTypeHandlerRegistry registry =
            new AiResourceSearchTypeHandlerRegistry(List.of(
                new StoredAiResourceSearchTypeHandler(resourceManager, indexContentLoader),
                new McpAiResourceSearchTypeHandler(mcpServerOperationService)));
        return new AiResourceIndexServiceImpl(repository, embeddingService, vectorIndex,
            indexEnhancementService, registry);
    }
    
    private AiResourceSearchDocument enhancementEntry() {
        AiResourceSearchDocument result = new AiResourceSearchDocument();
        result.setId(10L);
        result.setNamespaceId("public");
        result.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        result.setResourceName("api-helper");
        result.setResourceVersion("1.0.0");
        return result;
    }
    
    private void prepareEnhancement(AiResourceSearchDocument entry) throws Exception {
        when(repository.findEntry("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper"))
            .thenReturn(entry);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_ONLINE));
        when(enhancementService.ready()).thenReturn(true);
        when(repository.listChunks(10L)).thenReturn(Collections.emptyList());
        when(enhancementService.enhanceWithResult(any(), anyList(), anyList()))
            .thenReturn(new AiResourceIndexEnhancementResult(Collections.emptyList(),
                "fingerprint-v1"));
    }
    
    private AiResource meta() {
        AiResource meta = new AiResource();
        meta.setNamespaceId("public");
        meta.setName("api-helper");
        meta.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        meta.setDesc("Generate API parameter tables");
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setBizTags(JacksonUtils.toJson(List.of("api", "documentation")));
        meta.setExt(JacksonUtils.toJson(Map.of("inputTypes", List.of("json"),
            "outputTypes", List.of("markdown"), "riskLevel", "low")));
        meta.setVersionInfo(JacksonUtils.toJson(Map.of("labels", Map.of("latest", "1.0.0"))));
        return meta;
    }
    
    private AiResourceVersion version(String status) {
        AiResourceVersion version = new AiResourceVersion();
        version.setNamespaceId("public");
        version.setName("api-helper");
        version.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        version.setVersion("1.0.0");
        version.setDesc("Extract API parameters");
        version.setStatus(status);
        return version;
    }
    
    private McpServerDetailInfo mcpServer() {
        McpTool tool = new McpTool();
        tool.setName("avatar_video");
        tool.setDescription("Create avatar video from an image and voice.");
        McpToolSpecification toolSpec = new McpToolSpecification();
        toolSpec.setTools(List.of(tool));
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        versionDetail.setIs_latest(true);
        McpServerDetailInfo mcpServer = new McpServerDetailInfo();
        mcpServer.setId("mcp-avatar");
        mcpServer.setName("Avatar MCP");
        mcpServer.setDescription("MCP server for avatar videos");
        mcpServer.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        mcpServer.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        mcpServer.setEnabled(true);
        mcpServer.setVersionDetail(versionDetail);
        mcpServer.setToolSpec(toolSpec);
        return mcpServer;
    }
}
