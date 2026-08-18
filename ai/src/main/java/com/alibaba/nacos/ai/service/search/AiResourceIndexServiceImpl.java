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

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorChunk;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Default AI resource index builder.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceIndexServiceImpl implements AiResourceIndexService {
    
    private final AiResourceSearchRepository repository;
    
    private final AiResourceEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    private final AiResourceSearchChunkBuilder chunkBuilder;
    
    private final AiResourceIndexEnhancementService enhancementService;
    
    private final AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    @Autowired
    public AiResourceIndexServiceImpl(AiResourceSearchRepository repository,
        AiResourceEmbeddingService embeddingService,
        AiResourceVectorIndex vectorIndex, AiResourceIndexEnhancementService enhancementService,
        AiResourceSearchTypeHandlerRegistry typeHandlerRegistry) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.enhancementService =
            enhancementService == null ? AiResourceIndexEnhancementService.NOOP
                : enhancementService;
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.chunkBuilder = new AiResourceSearchChunkBuilder();
    }
    
    @Override
    public void rebuildAiResource(String namespaceId, String resourceType, String name,
        String version) throws NacosException {
        if (StringUtils.isBlank(version)) {
            rebuildLatestAiResource(namespaceId, resourceType, name);
            return;
        }
        AiResourceSearchTypeHandler handler = typeHandlerRegistry.get(resourceType);
        AiResourceIndexProjection projection = handler == null ? null
            : handler.project(namespaceId, resourceType, name, version);
        if (projection == null) {
            deleteResourceVersion(namespaceId, resourceType, name, version);
            return;
        }
        replace(projection);
    }
    
    @Override
    public boolean rebuildLatestAiResource(String namespaceId, String resourceType, String name)
        throws NacosException {
        AiResourceSearchTypeHandler handler = typeHandlerRegistry.get(resourceType);
        AiResourceIndexProjection projection = handler == null ? null
            : handler.project(namespaceId, resourceType, name, null);
        if (projection == null) {
            deleteResource(namespaceId, resourceType, name);
            return false;
        }
        deleteResource(namespaceId, resourceType, name);
        replace(projection);
        return true;
    }
    
    @Override
    public boolean isEnhancementRequested() {
        return enhancementService.requested();
    }
    
    @Override
    public String enhancementFingerprint() {
        return enhancementService.fingerprint();
    }
    
    @Override
    public boolean enhanceLatestAiResource(String namespaceId, String resourceType, String name)
        throws Exception {
        return enhanceLatestAiResource(namespaceId, resourceType, name, () -> true) != null;
    }
    
    @Override
    public String enhanceLatestAiResource(String namespaceId, String resourceType, String name,
        BooleanSupplier ownership) throws Exception {
        AiResourceSearchDocument entry = repository.findEntry(namespaceId, resourceType, name);
        if (entry == null) {
            return null;
        }
        AiResourceSearchTypeHandler handler = typeHandlerRegistry.get(resourceType);
        AiResourceIndexProjection projection = handler == null ? null
            : handler.project(namespaceId, resourceType, name, null);
        if (projection == null || !Objects.equals(entry.getResourceVersion(),
            projection.getDocument().getResourceVersion())) {
            return null;
        }
        return enhance(entry, projection.getEnhancementContents(), ownership);
    }
    
    @Override
    public void deleteResource(String namespaceId, String resourceType, String resourceName) {
        if (StringUtils.isBlank(resourceName)) {
            return;
        }
        if (vectorIndex.available()) {
            vectorIndex.deleteByResource(namespaceId, resourceType, resourceName);
        }
        repository.deleteByResource(namespaceId, resourceType, resourceName);
    }
    
    @Override
    public void deleteResourceVersion(String namespaceId, String resourceType,
        String resourceName, String resourceVersion) {
        if (StringUtils.isBlank(resourceName) || StringUtils.isBlank(resourceVersion)) {
            return;
        }
        if (vectorIndex.available()) {
            vectorIndex.deleteByResourceVersion(namespaceId, resourceType, resourceName,
                resourceVersion);
        }
        repository.deleteByResourceVersion(namespaceId, resourceType, resourceName,
            resourceVersion);
    }
    
    private void replace(AiResourceIndexProjection projection) {
        AiResourceSearchDocument entry = projection.getDocument();
        boolean vectorAvailable = vectorIndex.available();
        if (vectorAvailable) {
            entry.setStatus(AiResourceSearchConstants.STATUS_PENDING);
        }
        List<AiResourceSearchChunk> persistedChunks =
            repository.replaceEntry(entry, projection.getChunks());
        if (vectorAvailable) {
            vectorIndex.replaceResourceVersion(entry.getNamespaceId(), entry.getResourceType(),
                entry.getResourceName(), entry.getResourceVersion(),
                vectorDocuments(persistedChunks));
            repository.updateEntryStatus(entry.getId(), AiResourceSearchConstants.STATUS_ENABLED);
        }
    }
    
    private String enhance(AiResourceSearchDocument entry,
        List<AiResourceIndexEnhancementContent> contents, BooleanSupplier ownership)
        throws Exception {
        if (!enhancementService.ready()) {
            throw new IllegalStateException("AI resource index enhancement is not configured");
        }
        List<AiResourceSearchChunk> baseChunks = new ArrayList<>();
        for (AiResourceSearchChunk chunk : repository.listChunks(entry.getId())) {
            if (!isEnhancementChunk(chunk)) {
                baseChunks.add(chunk);
            }
        }
        AiResourceIndexEnhancementResult enhancement =
            enhancementService.enhanceWithResult(entry, baseChunks, contents);
        if (!ownership.getAsBoolean()) {
            return null;
        }
        List<AiResourceSearchChunk> enhancedChunks =
            chunkBuilder.buildEnhancementChunks(entry, enhancement.getChunks());
        enhancedChunks.removeIf(chunk -> !isEnhancementChunk(chunk));
        boolean vectorAvailable = vectorIndex.available();
        if (vectorAvailable) {
            repository.updateEntryStatus(entry.getId(), AiResourceSearchConstants.STATUS_PENDING);
        }
        if (!ownership.getAsBoolean()) {
            return null;
        }
        List<AiResourceSearchChunk> allChunks =
            repository.replaceEnhancementChunks(entry, enhancedChunks);
        if (vectorAvailable) {
            if (!ownership.getAsBoolean()) {
                return null;
            }
            vectorIndex.replaceResourceVersion(entry.getNamespaceId(), entry.getResourceType(),
                entry.getResourceName(), entry.getResourceVersion(), vectorDocuments(allChunks));
            repository.updateEntryStatus(entry.getId(), AiResourceSearchConstants.STATUS_ENABLED);
        }
        return enhancement.getFingerprint();
    }
    
    private boolean isEnhancementChunk(AiResourceSearchChunk chunk) {
        return AiResourceSearchConstants.CHUNK_TYPE_AI_SUMMARY.equals(chunk.getChunkType())
            || AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT.equals(chunk.getChunkType())
            || AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM.equals(chunk.getChunkType());
    }
    
    private List<AiResourceVectorDocument> vectorDocuments(List<AiResourceSearchChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiResourceVectorDocument> documents = new ArrayList<>();
        for (AiResourceSearchChunk chunk : chunks) {
            documents
                .add(new AiResourceVectorDocument(toVectorChunk(chunk), embeddingService.model(),
                    embeddingService.embed(chunk.getCanonicalText())));
        }
        return documents;
    }
    
    private AiResourceVectorChunk toVectorChunk(AiResourceSearchChunk chunk) {
        AiResourceVectorChunk result = new AiResourceVectorChunk();
        result.setId(chunk.getId());
        result.setDocumentId(chunk.getDocumentId());
        result.setNamespaceId(chunk.getNamespaceId());
        result.setResourceType(chunk.getResourceType());
        result.setResourceName(chunk.getResourceName());
        result.setResourceVersion(chunk.getResourceVersion());
        result.setChunkType(chunk.getChunkType());
        return result;
    }
    
}
