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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts Nacos AI resources to search index entries.
 *
 * @author nacos
 */
public class AiResourceSearchDocumentBuilder {
    
    private static final String RESOURCE_TYPE_SKILL = AiResourceConstants.RESOURCE_TYPE_SKILL;
    
    private static final String RESOURCE_TYPE_PROMPT = AiResourceConstants.RESOURCE_TYPE_PROMPT;
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    /**
     * Build an search document from a skill or prompt meta/version pair.
     */
    public AiResourceSearchDocument fromAiResource(AiResource meta, AiResourceVersion version) {
        Map<String, Object> metadata = baseMetadata(meta.getNamespaceId(), meta.getType(),
            meta.getName(), version.getVersion(), AiResourceManager.resolveScope(meta));
        if (RESOURCE_TYPE_SKILL.equals(meta.getType())) {
            metadata.put("entrypoint", "SKILL.md");
        }
        Map<String, Object> ext = parseMap(meta.getExt());
        metadata.putAll(extractMetadata(ext));
        List<String> tags = parseStringList(meta.getBizTags());
        List<String> capabilities = capabilities(meta.getType(), tags, ext);
        AiResourceSearchDocument entry =
            baseEntry(meta.getNamespaceId(), meta.getType(), meta.getName(),
                version.getVersion(), meta.getName());
        entry.setDescription(firstNotBlank(version.getDesc(), meta.getDesc()));
        entry.setTags(JacksonUtils.toJson(tags));
        entry.setCapabilities(JacksonUtils.toJson(capabilities));
        entry.setRepresentativeQueries(JacksonUtils.toJson(representativeQueries(meta, version)));
        entry.setMetadata(JacksonUtils.toJson(metadata));
        entry.setSourceDigest(sourceDigest(meta, version, metadata));
        entry.setGmtModified(resolveModified(meta.getGmtModified(), version.getGmtModified()));
        return entry;
    }
    
    /**
     * Build an search document from an MCP server specification.
     */
    public AiResourceSearchDocument fromMcpServer(String namespaceId,
        McpServerBasicInfo mcpServer) {
        String resourceVersion = resolveMcpVersion(mcpServer);
        String resourceName = mcpServer.getName();
        Map<String, Object> metadata =
            baseMetadata(namespaceId, AiResourceConstants.RESOURCE_TYPE_MCP,
                resourceName, resourceVersion, null);
        metadata.put("mcpName", mcpServer.getName());
        metadata.put("mcpServerId", mcpServer.getId());
        metadata.put("protocol", mcpServer.getProtocol());
        metadata.put("frontProtocol", mcpServer.getFrontProtocol());
        metadata.put("enabled", mcpServer.isEnabled());
        metadata.put("status", mcpServer.getStatus());
        if (StringUtils.isNotBlank(mcpServer.getWebsiteUrl())) {
            metadata.put("websiteUrl", mcpServer.getWebsiteUrl());
        }
        List<String> capabilities = capabilities(mcpServer.getCapabilities());
        AiResourceSearchDocument entry =
            baseEntry(namespaceId, AiResourceConstants.RESOURCE_TYPE_MCP, resourceName,
                resourceVersion, mcpServer.getName());
        entry.setDescription(mcpServer.getDescription());
        entry.setTags(JacksonUtils.toJson(Collections.emptyList()));
        entry.setCapabilities(JacksonUtils.toJson(capabilities));
        entry.setRepresentativeQueries(JacksonUtils.toJson(representativeQueries(mcpServer)));
        entry.setMetadata(JacksonUtils.toJson(metadata));
        entry.setSourceDigest(sourceDigest(mcpServer, metadata));
        return entry;
    }
    
    private AiResourceSearchDocument baseEntry(String namespaceId, String resourceType,
        String resourceName,
        String resourceVersion, String displayName) {
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setNamespaceId(namespaceId);
        entry.setResourceType(resourceType);
        entry.setResourceName(resourceName);
        entry.setResourceVersion(resourceVersion);
        entry.setDisplayName(displayName);
        entry.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        entry.setGenerateMode(AiResourceSearchConstants.GENERATE_MODE_AUTO);
        return entry;
    }
    
    private Map<String, Object> baseMetadata(String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String scope) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("namespaceId", namespaceId);
        metadata.put("resourceType", resourceType);
        metadata.put("resourceName", resourceName);
        metadata.put("resourceVersion", resourceVersion);
        if (StringUtils.isNotBlank(scope)) {
            metadata.put("scope", scope);
        }
        return metadata;
    }
    
    private Map<String, Object> extractMetadata(Map<String, Object> ext) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "inputTypes", ext.get("inputTypes"));
        putIfPresent(metadata, "outputTypes", ext.get("outputTypes"));
        putIfPresent(metadata, "sideEffects", ext.get("sideEffects"));
        putIfPresent(metadata, "riskLevel", ext.get("riskLevel"));
        putIfPresent(metadata, "notFor", ext.get("notFor"));
        return metadata;
    }
    
    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && StringUtils.isBlank((String) value)) {
            return;
        }
        metadata.put(key, value);
    }
    
    private List<String> capabilities(String resourceType, List<String> tags,
        Map<String, Object> ext) {
        List<String> capabilities = new ArrayList<>();
        capabilities.add(resourceType);
        addAll(capabilities, tags);
        addAll(capabilities, toStringList(ext.get("capabilities")));
        return dedupe(capabilities);
    }
    
    private List<String> capabilities(List<McpCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (McpCapability capability : capabilities) {
            if (capability != null) {
                result.add(capability.name().toLowerCase(Locale.ROOT));
            }
        }
        return dedupe(result);
    }
    
    private List<String> representativeQueries(AiResource meta, AiResourceVersion version) {
        List<String> queries = new ArrayList<>();
        addIfNotBlank(queries, meta.getName());
        addIfNotBlank(queries, meta.getDesc());
        addIfNotBlank(queries, version.getDesc());
        return dedupe(queries);
    }
    
    private List<String> representativeQueries(McpServerBasicInfo mcpServer) {
        List<String> queries = new ArrayList<>();
        addIfNotBlank(queries, mcpServer.getName());
        addIfNotBlank(queries, mcpServer.getDescription());
        return dedupe(queries);
    }
    
    private String sourceDigest(AiResource meta, AiResourceVersion version,
        Map<String, Object> metadata) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("metaModified", meta.getGmtModified());
        digest.put("versionModified", version.getGmtModified());
        digest.put("status", meta.getStatus());
        digest.put("versionStatus", version.getStatus());
        digest.put("description", firstNotBlank(version.getDesc(), meta.getDesc()));
        digest.put("bizTags", meta.getBizTags());
        digest.put("metadata", metadata);
        return md5(digest);
    }
    
    private String sourceDigest(McpServerBasicInfo mcpServer, Map<String, Object> metadata) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("name", mcpServer.getName());
        digest.put("description", mcpServer.getDescription());
        digest.put("version", resolveMcpVersion(mcpServer));
        digest.put("enabled", mcpServer.isEnabled());
        digest.put("status", mcpServer.getStatus());
        digest.put("capabilities", capabilities(mcpServer.getCapabilities()));
        digest.put("metadata", metadata);
        return md5(digest);
    }
    
    private String resolveMcpVersion(McpServerBasicInfo mcpServer) {
        ServerVersionDetail versionDetail = mcpServer.getVersionDetail();
        if (versionDetail != null && StringUtils.isNotBlank(versionDetail.getVersion())) {
            return versionDetail.getVersion();
        }
        return mcpServer.getVersion();
    }
    
    private Timestamp resolveModified(Timestamp metaModified, Timestamp versionModified) {
        return versionModified == null ? metaModified : versionModified;
    }
    
    private Map<String, Object> parseMap(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = JacksonUtils.toObj(value, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
    
    private List<String> parseStringList(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = JacksonUtils.toObj(value, STRING_LIST_TYPE);
            if (parsed != null) {
                return dedupe(parsed);
            }
        } catch (Exception ignored) {
            // Fall back to comma-separated values below.
        }
        List<String> result = new ArrayList<>();
        for (String each : value.split(",")) {
            addIfNotBlank(result, each);
        }
        return dedupe(result);
    }
    
    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object each : (Collection<?>) value) {
                if (each != null) {
                    addIfNotBlank(result, String.valueOf(each));
                }
            }
            return result;
        }
        if (value instanceof String) {
            return parseStringList((String) value);
        }
        return Collections.singletonList(String.valueOf(value));
    }
    
    private void addAll(List<String> result, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addIfNotBlank(result, value);
        }
    }
    
    private void addIfNotBlank(List<String> result, String value) {
        if (StringUtils.isNotBlank(value)) {
            result.add(value.trim());
        }
    }
    
    private List<String> dedupe(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.isBlank(value) || containsIgnoreCase(result, value)) {
                continue;
            }
            result.add(value.trim());
        }
        return result;
    }
    
    private boolean containsIgnoreCase(List<String> values, String expected) {
        String normalized = expected.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (normalized.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
    
    private String md5(Map<String, Object> digest) {
        return MD5Utils.md5Hex(JacksonUtils.toJson(digest), StandardCharsets.UTF_8.name());
    }
}
