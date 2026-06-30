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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default ARD index builder.
 *
 * @author nacos
 */
@Service
public class ArdIndexBuildServiceImpl implements ArdIndexBuildService {
    
    private final AiResourceManager resourceManager;
    
    private final ArdIndexRepository repository;
    
    private final ArdEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    private final ArdEntryBuilder entryBuilder;
    
    private final ArdChunkBuilder chunkBuilder;
    
    public ArdIndexBuildServiceImpl(AiResourceManager resourceManager,
        ArdIndexRepository repository, ArdEmbeddingService embeddingService,
        AiResourceVectorIndex vectorIndex) {
        this.resourceManager = resourceManager;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
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
        replace(entryBuilder.fromAiResource(meta, resourceVersion));
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
        rebuildAiResource(namespaceId, resourceType, name, latestVersion);
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
        List<ArdChunk> chunks = chunkBuilder.buildChunks(entry);
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
