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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.service.ard.vector.AiResourceVectorDocument;
import com.alibaba.nacos.ai.service.ard.vector.AiResourceVectorIndex;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.ExecutorUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Default ARD index builder.
 *
 * @author nacos
 */
@Service
public class ArdIndexBuildServiceImpl implements ArdIndexBuildService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdIndexBuildServiceImpl.class);
    
    private final AiResourceManager resourceManager;
    
    private final ArdIndexRepository repository;
    
    private final ArdEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    private final ArdEntryBuilder entryBuilder;
    
    private final ArdChunkBuilder chunkBuilder;
    
    private final ArdIndexEnhancementService enhancementService;
    
    private final ArdIndexContentLoader contentLoader;
    
    private final Executor enhancementExecutor;
    
    @Autowired
    public ArdIndexBuildServiceImpl(AiResourceManager resourceManager,
        ArdIndexRepository repository, ArdEmbeddingService embeddingService,
        AiResourceVectorIndex vectorIndex, ArdIndexEnhancementService enhancementService,
        ArdIndexContentLoader contentLoader) {
        this(resourceManager, repository, embeddingService, vectorIndex, enhancementService,
            contentLoader, ExecutorUtils.getArdIndexEnhancementExecutor());
    }
    
    public ArdIndexBuildServiceImpl(AiResourceManager resourceManager,
        ArdIndexRepository repository, ArdEmbeddingService embeddingService,
        AiResourceVectorIndex vectorIndex) {
        this(resourceManager, repository, embeddingService, vectorIndex,
            ArdIndexEnhancementService.NOOP, ArdIndexContentLoader.NOOP, Runnable::run);
    }
    
    ArdIndexBuildServiceImpl(AiResourceManager resourceManager, ArdIndexRepository repository,
        ArdEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex,
        ArdIndexEnhancementService enhancementService, Executor enhancementExecutor) {
        this(resourceManager, repository, embeddingService, vectorIndex, enhancementService,
            ArdIndexContentLoader.NOOP, enhancementExecutor);
    }
    
    ArdIndexBuildServiceImpl(AiResourceManager resourceManager, ArdIndexRepository repository,
        ArdEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex,
        ArdIndexEnhancementService enhancementService, ArdIndexContentLoader contentLoader,
        Executor enhancementExecutor) {
        this.resourceManager = resourceManager;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.enhancementService =
            enhancementService == null ? ArdIndexEnhancementService.NOOP : enhancementService;
        this.contentLoader = contentLoader == null ? ArdIndexContentLoader.NOOP : contentLoader;
        this.enhancementExecutor = enhancementExecutor;
        this.entryBuilder = new ArdEntryBuilder();
        this.chunkBuilder = new ArdChunkBuilder();
    }
    
    @Override
    public void rebuildAiResource(String namespaceId, String resourceType, String name,
        String version) throws NacosException {
        if (StringUtils.isBlank(version)) {
            rebuildLatestAiResource(namespaceId, resourceType, name);
            return;
        }
        AiResource meta = resourceManager.findMeta(namespaceId, name, resourceType);
        AiResourceVersion resourceVersion =
            resourceManager.findVersion(namespaceId, name, resourceType, version);
        if (!isIndexable(meta, resourceVersion)) {
            deleteResourceVersion(namespaceId, resourceType, name, version);
            return;
        }
        replace(entryBuilder.fromAiResource(meta, resourceVersion), resourceVersion);
    }
    
    @Override
    public void rebuildLatestAiResource(String namespaceId, String resourceType, String name)
        throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, name, resourceType);
        if (meta == null) {
            deleteResource(namespaceId, resourceType, name);
            return;
        }
        String latestVersion = AiResourceManager.resolveVersion(meta, null,
            AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            deleteResource(namespaceId, resourceType, name);
            return;
        }
        AiResourceVersion resourceVersion =
            resourceManager.findVersion(namespaceId, name, resourceType, latestVersion);
        if (!isIndexable(meta, resourceVersion)) {
            deleteResource(namespaceId, resourceType, name);
            return;
        }
        deleteResource(namespaceId, resourceType, name);
        replace(entryBuilder.fromAiResource(meta, resourceVersion), resourceVersion);
    }
    
    @Override
    public void rebuildMcpServer(String namespaceId, McpServerBasicInfo mcpServer) {
        if (mcpServer == null) {
            return;
        }
        String resourceName = firstNotBlank(mcpServer.getId(), mcpServer.getName());
        String resourceVersion = resolveMcpVersion(mcpServer);
        if (!isIndexable(mcpServer) || StringUtils.isBlank(resourceName)
            || StringUtils.isBlank(resourceVersion)) {
            deleteResource(namespaceId, ArdIndexConstants.RESOURCE_TYPE_MCP, resourceName);
            return;
        }
        replace(entryBuilder.fromMcpServer(namespaceId, mcpServer));
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
    
    private void replace(ArdEntry entry) {
        replace(entry, null);
    }
    
    private void replace(ArdEntry entry, AiResourceVersion resourceVersion) {
        List<ArdIndexEnhancementContent> contents = loadContents(entry, resourceVersion);
        List<ArdChunk> chunks = new ArrayList<>(chunkBuilder.buildChunks(entry));
        chunks.addAll(chunkBuilder.buildSkillContentChunks(entry, contents));
        if (vectorIndex.available()) {
            vectorIndex.deleteByResourceVersion(entry.getNamespaceId(), entry.getResourceType(),
                entry.getResourceName(), entry.getResourceVersion());
        }
        List<ArdChunk> persistedChunks = repository.replaceEntry(entry, chunks);
        if (vectorIndex.available()) {
            vectorIndex.replaceResourceVersion(entry.getNamespaceId(), entry.getResourceType(),
                entry.getResourceName(), entry.getResourceVersion(),
                vectorDocuments(persistedChunks));
        }
        submitEnhancement(entry, persistedChunks, contents);
    }
    
    private List<ArdIndexEnhancementContent> loadContents(ArdEntry entry,
        AiResourceVersion resourceVersion) {
        try {
            return contentLoader.load(entry, resourceVersion);
        } catch (Exception e) {
            LOGGER.warn("Failed to load ARD index source content for {}:{}@{}",
                entry.getResourceType(), entry.getResourceName(), entry.getResourceVersion(), e);
            return Collections.emptyList();
        }
    }
    
    private void submitEnhancement(ArdEntry entry, List<ArdChunk> persistedChunks,
        List<ArdIndexEnhancementContent> contents) {
        if (!enhancementService.enabled()) {
            return;
        }
        enhancementExecutor.execute(() -> appendEnhancedChunks(entry, persistedChunks, contents));
    }
    
    private void appendEnhancedChunks(ArdEntry entry, List<ArdChunk> persistedChunks,
        List<ArdIndexEnhancementContent> contents) {
        try {
            List<ArdIndexEnhancementChunk> enhancements =
                enhancementService.enhance(entry, persistedChunks, contents);
            List<ArdChunk> enhancedChunks =
                chunkBuilder.buildEnhancementChunks(entry, enhancements);
            if (enhancedChunks.isEmpty()) {
                return;
            }
            List<ArdChunk> persistedEnhancedChunks =
                repository.appendChunks(entry, enhancedChunks);
            if (vectorIndex.available()) {
                vectorIndex.addDocuments(vectorDocuments(persistedEnhancedChunks));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enhance ARD index for {}:{}@{}", entry.getResourceType(),
                entry.getResourceName(), entry.getResourceVersion(), e);
        }
    }
    
    private List<AiResourceVectorDocument> vectorDocuments(List<ArdChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiResourceVectorDocument> documents = new ArrayList<>();
        for (ArdChunk chunk : chunks) {
            documents.add(new AiResourceVectorDocument(chunk, embeddingService.model(),
                embeddingService.embed(chunk.getCanonicalText())));
        }
        return documents;
    }
    
    private boolean isIndexable(AiResource meta, AiResourceVersion version) {
        return meta != null && version != null
            && AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())
            && AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus());
    }
    
    private boolean isIndexable(McpServerBasicInfo mcpServer) {
        return mcpServer.isEnabled()
            && AiConstants.Mcp.MCP_STATUS_ACTIVE.equalsIgnoreCase(mcpServer.getStatus());
    }
    
    private String resolveMcpVersion(McpServerBasicInfo mcpServer) {
        if (mcpServer.getVersionDetail() != null
            && StringUtils.isNotBlank(mcpServer.getVersionDetail().getVersion())) {
            return mcpServer.getVersionDetail().getVersion();
        }
        return mcpServer.getVersion();
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
}
