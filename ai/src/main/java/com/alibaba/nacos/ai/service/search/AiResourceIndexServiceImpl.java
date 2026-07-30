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
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorChunk;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceIndexServiceImpl.class);
    
    private static final int DEFAULT_MAX_MCP_CONTENT_CHARS = 12000;
    
    private final AiResourceManager resourceManager;
    
    private final AiResourceSearchRepository repository;
    
    private final AiResourceEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    private final AiResourceSearchDocumentBuilder entryBuilder;
    
    private final AiResourceSearchChunkBuilder chunkBuilder;
    
    private final AiResourceIndexEnhancementService enhancementService;
    
    private final AiResourceIndexContentLoader contentLoader;
    
    @Autowired
    public AiResourceIndexServiceImpl(AiResourceManager resourceManager,
        AiResourceSearchRepository repository, AiResourceEmbeddingService embeddingService,
        AiResourceVectorIndex vectorIndex, AiResourceIndexEnhancementService enhancementService,
        AiResourceIndexContentLoader contentLoader) {
        this.resourceManager = resourceManager;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.enhancementService =
            enhancementService == null ? AiResourceIndexEnhancementService.NOOP
                : enhancementService;
        this.contentLoader =
            contentLoader == null ? AiResourceIndexContentLoader.NOOP : contentLoader;
        this.entryBuilder = new AiResourceSearchDocumentBuilder();
        this.chunkBuilder = new AiResourceSearchChunkBuilder();
    }
    
    public AiResourceIndexServiceImpl(AiResourceManager resourceManager,
        AiResourceSearchRepository repository, AiResourceEmbeddingService embeddingService,
        AiResourceVectorIndex vectorIndex) {
        this(resourceManager, repository, embeddingService, vectorIndex,
            AiResourceIndexEnhancementService.NOOP, AiResourceIndexContentLoader.NOOP);
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
    public boolean rebuildLatestAiResource(String namespaceId, String resourceType, String name)
        throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, name, resourceType);
        if (meta == null) {
            deleteResource(namespaceId, resourceType, name);
            return false;
        }
        String latestVersion = AiResourceManager.resolveVersion(meta, null,
            AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            deleteResource(namespaceId, resourceType, name);
            return false;
        }
        AiResourceVersion resourceVersion =
            resourceManager.findVersion(namespaceId, name, resourceType, latestVersion);
        if (!isIndexable(meta, resourceVersion)) {
            deleteResource(namespaceId, resourceType, name);
            return false;
        }
        deleteResource(namespaceId, resourceType, name);
        replace(entryBuilder.fromAiResource(meta, resourceVersion), resourceVersion);
        return true;
    }
    
    @Override
    public boolean rebuildMcpServer(String namespaceId, McpServerBasicInfo mcpServer) {
        if (mcpServer == null) {
            return false;
        }
        String resourceName = firstNotBlank(mcpServer.getId(), mcpServer.getName());
        String resourceVersion = resolveMcpVersion(mcpServer);
        if (!isIndexable(mcpServer) || StringUtils.isBlank(resourceName)
            || StringUtils.isBlank(resourceVersion)) {
            deleteResource(namespaceId, AiResourceConstants.RESOURCE_TYPE_MCP, resourceName);
            return false;
        }
        replace(entryBuilder.fromMcpServer(namespaceId, mcpServer), null, mcpContents(mcpServer));
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
        AiResource meta = resourceManager.findMeta(namespaceId, name, resourceType);
        if (meta == null) {
            return null;
        }
        String latestVersion = AiResourceManager.resolveVersion(meta, null,
            AiResourceConstants.LABEL_LATEST);
        if (!Objects.equals(entry.getResourceVersion(), latestVersion)) {
            return null;
        }
        AiResourceVersion resourceVersion =
            resourceManager.findVersion(namespaceId, name, resourceType, latestVersion);
        if (!isIndexable(meta, resourceVersion)) {
            return null;
        }
        return enhance(entry, contentLoader.load(entry, resourceVersion), ownership);
    }
    
    @Override
    public boolean enhanceMcpServer(String namespaceId, McpServerBasicInfo mcpServer)
        throws Exception {
        return enhanceMcpServer(namespaceId, mcpServer, () -> true) != null;
    }
    
    @Override
    public String enhanceMcpServer(String namespaceId, McpServerBasicInfo mcpServer,
        BooleanSupplier ownership) throws Exception {
        if (mcpServer == null) {
            return null;
        }
        String resourceName = firstNotBlank(mcpServer.getId(), mcpServer.getName());
        String resourceVersion = resolveMcpVersion(mcpServer);
        AiResourceSearchDocument entry = repository.findEntry(namespaceId,
            AiResourceConstants.RESOURCE_TYPE_MCP, resourceName);
        if (entry == null || !isIndexable(mcpServer)) {
            return null;
        }
        if (!Objects.equals(entry.getResourceVersion(), resourceVersion)) {
            return null;
        }
        return enhance(entry, mcpContents(mcpServer), ownership);
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
    
    private void replace(AiResourceSearchDocument entry, AiResourceVersion resourceVersion) {
        replace(entry, resourceVersion, loadContents(entry, resourceVersion));
    }
    
    private void replace(AiResourceSearchDocument entry, AiResourceVersion resourceVersion,
        List<AiResourceIndexEnhancementContent> contents) {
        List<AiResourceSearchChunk> chunks = new ArrayList<>(chunkBuilder.buildChunks(entry));
        chunks.addAll(chunkBuilder.buildSourceContentChunks(entry, contents));
        boolean vectorAvailable = vectorIndex.available();
        if (vectorAvailable) {
            entry.setStatus(AiResourceSearchConstants.STATUS_PENDING);
        }
        List<AiResourceSearchChunk> persistedChunks = repository.replaceEntry(entry, chunks);
        if (vectorAvailable) {
            vectorIndex.replaceResourceVersion(entry.getNamespaceId(), entry.getResourceType(),
                entry.getResourceName(), entry.getResourceVersion(),
                vectorDocuments(persistedChunks));
            repository.updateEntryStatus(entry.getId(), AiResourceSearchConstants.STATUS_ENABLED);
        }
    }
    
    private List<AiResourceIndexEnhancementContent> loadContents(AiResourceSearchDocument entry,
        AiResourceVersion resourceVersion) {
        try {
            return contentLoader.load(entry, resourceVersion);
        } catch (Exception e) {
            LOGGER.warn("Failed to load AI resource index source content for {}:{}@{}",
                entry.getResourceType(), entry.getResourceName(), entry.getResourceVersion(), e);
            return Collections.emptyList();
        }
    }
    
    private List<AiResourceIndexEnhancementContent> mcpContents(McpServerBasicInfo mcpServer) {
        List<AiResourceIndexEnhancementContent> contents = new ArrayList<>();
        addMcpContent(contents, "mcp-server.json", mcpServerText(mcpServer));
        if (mcpServer instanceof McpServerDetailInfo detail) {
            addMcpContent(contents, "mcp-tools.json", mcpToolText(detail.getToolSpec()));
            addMcpContent(contents, "mcp-resources.json",
                mcpResourceText(detail.getResourceSpec()));
        }
        return contents;
    }
    
    private void addMcpContent(List<AiResourceIndexEnhancementContent> contents, String path,
        String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        contents
            .add(new AiResourceIndexEnhancementContent(path,
                limit(text, DEFAULT_MAX_MCP_CONTENT_CHARS)));
    }
    
    private String mcpServerText(McpServerBasicInfo mcpServer) {
        StringBuilder text = new StringBuilder();
        appendLine(text, "# MCP server");
        appendField(text, "name", mcpServer.getName());
        appendField(text, "description", mcpServer.getDescription());
        appendField(text, "protocol", mcpServer.getProtocol());
        appendField(text, "front protocol", mcpServer.getFrontProtocol());
        appendField(text, "website", mcpServer.getWebsiteUrl());
        if (mcpServer.getCapabilities() != null && !mcpServer.getCapabilities().isEmpty()) {
            appendLine(text, "capabilities: " + mcpCapabilities(mcpServer.getCapabilities()));
        }
        return text.toString();
    }
    
    private String mcpToolText(McpToolSpecification toolSpec) {
        if (toolSpec == null || toolSpec.getTools() == null || toolSpec.getTools().isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        appendLine(text, "# MCP tools");
        for (McpTool tool : toolSpec.getTools()) {
            if (tool == null) {
                continue;
            }
            appendLine(text, "## Tool " + tool.getName());
            appendLine(text, tool.getDescription());
            appendMap(text, "input schema", tool.getInputSchema());
            appendMap(text, "output schema", tool.getOutputSchema());
        }
        return text.toString();
    }
    
    private String mcpResourceText(McpResourceSpecification resourceSpec) {
        if (resourceSpec == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        appendResourceMaps(text, "# MCP resources", "resource", resourceSpec.getResources());
        appendResourceMaps(text, "# MCP resource templates", "resource template",
            resourceSpec.getResourceTemplates());
        return text.toString();
    }
    
    private void appendResourceMaps(StringBuilder text, String heading, String label,
        List<Map<String, Object>> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        appendLine(text, heading);
        for (Map<String, Object> resource : resources) {
            if (resource == null || resource.isEmpty()) {
                continue;
            }
            String resourceText = selectedResourceText(resource);
            if (StringUtils.isNotBlank(resourceText)) {
                appendLine(text, label + ": " + resourceText);
            }
        }
    }
    
    private String selectedResourceText(Map<String, Object> resource) {
        List<String> parts = new ArrayList<>();
        addMapValue(parts, resource, "name");
        addMapValue(parts, resource, "title");
        addMapValue(parts, resource, "description");
        addMapValue(parts, resource, "uri");
        addMapValue(parts, resource, "uriTemplate");
        return StringUtils.join(parts, " ");
    }
    
    private void addMapValue(List<String> parts, Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
            parts.add(key + ": " + value);
        }
    }
    
    private void appendMap(StringBuilder text, String label, Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        appendLine(text, label + ": " + map);
    }
    
    private String mcpCapabilities(Collection<McpCapability> capabilities) {
        List<String> values = new ArrayList<>();
        for (McpCapability capability : capabilities) {
            if (capability != null) {
                values.add(capability.name().toLowerCase(Locale.ROOT));
            }
        }
        return StringUtils.join(values, " ");
    }
    
    private void appendField(StringBuilder text, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            appendLine(text, key + ": " + value);
        }
    }
    
    private void appendLine(StringBuilder text, String line) {
        if (StringUtils.isBlank(line)) {
            return;
        }
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(line);
    }
    
    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
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
