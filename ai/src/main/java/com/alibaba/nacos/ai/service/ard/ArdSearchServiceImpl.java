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
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchQuery;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResult;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default local ARD Search implementation.
 *
 * <p>This first implementation builds ARD entries from current Nacos metadata at query time.
 * Persistent ARD Entry, chunk and vector indexes are intentionally left to later milestones.</p>
 *
 * @author nacos
 */
@Service
public class ArdSearchServiceImpl implements ArdSearchService {
    
    static final String FEDERATION_NONE = "none";
    
    static final String SOURCE_NACOS_LOCAL = "nacos-local";
    
    static final String MEDIA_TYPE_SKILL = "application/ai-skill+md";
    
    static final String MEDIA_TYPE_PROMPT = "application/vnd.nacos.ai-prompt+json";
    
    static final String MEDIA_TYPE_MCP = "application/mcp-server-card+json";
    
    private static final String RESOURCE_TYPE_MCP = "mcp";
    
    private static final int DEFAULT_PAGE_SIZE = 10;
    
    private static final int MAX_PAGE_SIZE = 50;
    
    private static final int MAX_RESOURCE_CANDIDATES = 200;
    
    private static final Set<String> SUPPORTED_FILTER_KEYS =
        new LinkedHashSet<>(Arrays.asList("type", "tags", "capabilities",
            "metadata.resourceType", "metadata.sideEffects", "metadata.riskLevel"));
    
    private final AiResourceManager resourceManager;
    
    private final McpServerOperationService mcpServerOperationService;
    
    public ArdSearchServiceImpl(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService) {
        this.resourceManager = resourceManager;
        this.mcpServerOperationService = mcpServerOperationService;
    }
    
    @Override
    public ArdSearchResponse search(ArdSearchRequest request) throws NacosException {
        SearchContext context = validateAndBuildContext(request);
        List<ArdSearchResult> candidates = new ArrayList<>();
        for (ResourceKind kind : context.kinds) {
            if (kind == ResourceKind.MCP) {
                collectMcpResults(context, candidates);
            } else {
                collectAiResourceResults(context, kind, candidates);
            }
        }
        candidates.sort(Comparator.comparing(ArdSearchResult::getScore,
            Comparator.nullsLast(Comparator.reverseOrder())));
        ArdSearchResponse response = new ArdSearchResponse();
        response.setResults(limit(candidates, context.pageSize));
        response.setReferrals(Collections.emptyList());
        return response;
    }
    
    private SearchContext validateAndBuildContext(ArdSearchRequest request)
        throws NacosApiException {
        if (request == null || request.getQuery() == null
            || StringUtils.isBlank(request.getQuery().getText())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING, "Required parameter `query.text` not present");
        }
        String federation = StringUtils.isBlank(request.getFederation()) ? FEDERATION_NONE
            : request.getFederation();
        if (!FEDERATION_NONE.equalsIgnoreCase(federation)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Only federation `none` is supported by Nacos Local ARD Search");
        }
        ArdSearchQuery query = request.getQuery();
        Map<String, List<String>> filter = normalizeFilter(query.getFilter());
        validateFilterKeys(filter.keySet());
        SearchContext context = new SearchContext();
        context.namespaceId = StringUtils.isBlank(request.getNamespaceId())
            ? com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID
            : request.getNamespaceId();
        context.text = query.getText().trim();
        context.normalizedText = normalize(context.text);
        context.filter = filter;
        context.pageSize = normalizePageSize(request.getPageSize());
        context.kinds = resolveKinds(filter);
        return context;
    }
    
    private Map<String, List<String>> normalizeFilter(Map<String, Object> rawFilter)
        throws NacosApiException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (rawFilter == null || rawFilter.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : rawFilter.entrySet()) {
            result.put(entry.getKey(), normalizeFilterValues(entry.getKey(), entry.getValue()));
        }
        return result;
    }
    
    private List<String> normalizeFilterValues(String key, Object value) throws NacosApiException {
        if (value instanceof String) {
            return Collections.singletonList((String) value);
        }
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object each : (Collection<?>) value) {
                if (each != null) {
                    result.add(String.valueOf(each));
                }
            }
            return result;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Request parameter `query.filter." + key + "` should be string or string array");
    }
    
    private void validateFilterKeys(Set<String> keys) throws NacosApiException {
        for (String key : keys) {
            if (!SUPPORTED_FILTER_KEYS.contains(key)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Unsupported ARD filter key: " + key);
            }
        }
    }
    
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
    
    private List<ResourceKind> resolveKinds(Map<String, List<String>> filter) {
        List<String> mediaTypes = filter.get("type");
        List<String> resourceTypes = filter.get("metadata.resourceType");
        List<ResourceKind> result = new ArrayList<>();
        for (ResourceKind kind : ResourceKind.values()) {
            if (matchesKindFilter(kind, mediaTypes, resourceTypes)) {
                result.add(kind);
            }
        }
        return result;
    }
    
    private boolean matchesKindFilter(ResourceKind kind, List<String> mediaTypes,
        List<String> resourceTypes) {
        if (mediaTypes != null && !containsIgnoreCase(mediaTypes, kind.mediaType)) {
            return false;
        }
        return resourceTypes == null || containsIgnoreCase(resourceTypes, kind.resourceType);
    }
    
    private void collectAiResourceResults(SearchContext context, ResourceKind kind,
        List<ArdSearchResult> candidates) {
        QueryCondition queryCondition = resourceManager.buildQueryCondition(context.namespaceId,
            kind.resourceType, null, null, VisibilityConstants.ACTION_READ);
        Page<AiResource> page =
            resourceManager.listMeta(queryCondition, 1, MAX_RESOURCE_CANDIDATES);
        if (page == null || page.getPageItems() == null) {
            return;
        }
        for (AiResource meta : page.getPageItems()) {
            ArdSearchResult result = buildAiResourceResult(context, kind, meta);
            if (result != null && matchesFieldFilters(context.filter, result)) {
                candidates.add(result);
            }
        }
    }
    
    private ArdSearchResult buildAiResourceResult(SearchContext context, ResourceKind kind,
        AiResource meta) {
        if (meta == null
            || !AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            return null;
        }
        double score = score(context.normalizedText, meta.getName(), meta.getDesc(),
            meta.getBizTags(), kind.resourceType);
        if (score <= 0) {
            return null;
        }
        String latestVersion = AiResourceManager.resolveVersion(meta, null,
            AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            return null;
        }
        AiResourceVersion version = resourceManager.findVersion(context.namespaceId,
            meta.getName(), kind.resourceType, latestVersion);
        if (version == null
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus())) {
            return null;
        }
        List<String> tags = parseTags(meta.getBizTags());
        ArdSearchResult result = baseResult(kind.mediaType, meta.getName(), version.getVersion());
        result.setIdentifier(buildIdentifier(context.namespaceId, kind.resourceType,
            meta.getName()));
        result.setUrl(buildNacosUrl(context.namespaceId, kind.resourceType, meta.getName(),
            version.getVersion()));
        result.setDescription(firstNotBlank(version.getDesc(), meta.getDesc()));
        result.setTags(tags);
        result.setCapabilities(tags);
        result.setUpdatedAt(formatTimestamp(
            version.getGmtModified() == null ? meta.getGmtModified() : version.getGmtModified()));
        result.setMetadata(buildMetadata(context.namespaceId, kind.resourceType, meta.getName(),
            version.getVersion(), meta.getScope()));
        result.setScore(score);
        return result;
    }
    
    private void collectMcpResults(SearchContext context, List<ArdSearchResult> candidates) {
        Page<McpServerBasicInfo> page = mcpServerOperationService.listMcpServerWithPage(
            context.namespaceId, context.text, Constants.MCP_LIST_SEARCH_BLUR, 1,
            MAX_RESOURCE_CANDIDATES);
        if (page == null || page.getPageItems() == null) {
            return;
        }
        for (McpServerBasicInfo mcp : page.getPageItems()) {
            ArdSearchResult result = buildMcpResult(context, mcp);
            if (result != null && matchesFieldFilters(context.filter, result)) {
                candidates.add(result);
            }
        }
    }
    
    private ArdSearchResult buildMcpResult(SearchContext context, McpServerBasicInfo mcp) {
        if (mcp == null || !mcp.isEnabled()
            || !AiConstants.Mcp.MCP_STATUS_ACTIVE.equalsIgnoreCase(mcp.getStatus())) {
            return null;
        }
        double score = score(context.normalizedText, mcp.getName(), mcp.getDescription(),
            capabilitiesToText(mcp.getCapabilities()), RESOURCE_TYPE_MCP);
        if (score <= 0) {
            return null;
        }
        String version = resolveMcpVersion(mcp);
        ArdSearchResult result = baseResult(MEDIA_TYPE_MCP, mcp.getName(), version);
        result.setIdentifier(buildIdentifier(context.namespaceId, RESOURCE_TYPE_MCP,
            firstNotBlank(mcp.getId(), mcp.getName())));
        result.setUrl(buildNacosUrl(context.namespaceId, RESOURCE_TYPE_MCP,
            firstNotBlank(mcp.getId(), mcp.getName()), version));
        result.setDescription(mcp.getDescription());
        result.setCapabilities(capabilitiesToList(mcp.getCapabilities()));
        result.setMetadata(buildMetadata(context.namespaceId, RESOURCE_TYPE_MCP, mcp.getName(),
            version, null));
        result.getMetadata().put("mcpServerId", mcp.getId());
        result.setScore(score);
        return result;
    }
    
    private ArdSearchResult baseResult(String mediaType, String displayName, String version) {
        ArdSearchResult result = new ArdSearchResult();
        result.setDisplayName(displayName);
        result.setType(mediaType);
        result.setVersion(version);
        result.setSource(SOURCE_NACOS_LOCAL);
        return result;
    }
    
    private Map<String, Object> buildMetadata(String namespaceId, String resourceType,
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
    
    private boolean matchesFieldFilters(Map<String, List<String>> filter, ArdSearchResult result) {
        return matchesAny(filter.get("tags"), result.getTags())
            && matchesAny(filter.get("capabilities"), result.getCapabilities())
            && matchesMetadata(filter.get("metadata.sideEffects"), result, "sideEffects")
            && matchesMetadata(filter.get("metadata.riskLevel"), result, "riskLevel");
    }
    
    private boolean matchesAny(List<String> expected, List<String> actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        for (String eachExpected : expected) {
            if (containsIgnoreCase(actual, eachExpected)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesMetadata(List<String> expected, ArdSearchResult result, String key) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        Object actual = result.getMetadata() == null ? null : result.getMetadata().get(key);
        return actual != null && containsIgnoreCase(expected, String.valueOf(actual));
    }
    
    private double score(String normalizedText, String name, String description, String tags,
        String resourceType) {
        double score = 0;
        score += contains(name, normalizedText) || contains(normalizedText, name) ? 1.0 : 0;
        score += contains(description, normalizedText) ? 0.6 : 0;
        score += contains(tags, normalizedText) ? 0.4 : 0;
        score += contains(resourceType, normalizedText) ? 0.2 : 0;
        return score;
    }
    
    private boolean contains(String text, String keyword) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(keyword)) {
            return false;
        }
        return normalize(text).contains(normalize(keyword));
    }
    
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
    
    private boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null || StringUtils.isBlank(expected)) {
            return false;
        }
        String normalizedExpected = normalize(expected);
        for (String value : values) {
            if (normalizedExpected.equals(normalize(value))) {
                return true;
            }
        }
        return false;
    }
    
    private List<String> parseTags(String bizTags) {
        if (StringUtils.isBlank(bizTags)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = JacksonUtils.toObj(bizTags, new TypeReference<List<String>>() {
            });
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
            // Fall back to comma-separated tags below.
        }
        List<String> tags = new ArrayList<>();
        for (String tag : bizTags.split(",")) {
            if (StringUtils.isNotBlank(tag)) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }
    
    private List<ArdSearchResult> limit(List<ArdSearchResult> candidates, int pageSize) {
        if (candidates.size() <= pageSize) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, pageSize));
    }
    
    private String capabilitiesToText(List<McpCapability> capabilities) {
        return StringUtils.join(capabilitiesToList(capabilities), ",");
    }
    
    private List<String> capabilitiesToList(List<McpCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (McpCapability capability : capabilities) {
            if (capability != null) {
                result.add(capability.name().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
    
    private String resolveMcpVersion(McpServerBasicInfo mcp) {
        ServerVersionDetail versionDetail = mcp.getVersionDetail();
        if (versionDetail != null && StringUtils.isNotBlank(versionDetail.getVersion())) {
            return versionDetail.getVersion();
        }
        return mcp.getVersion();
    }
    
    private String buildIdentifier(String namespaceId, String resourceType, String resourceName) {
        return "urn:air:nacos.local:" + namespaceId + ":" + resourceType + ":" + resourceName;
    }
    
    private String buildNacosUrl(String namespaceId, String resourceType, String resourceName,
        String version) {
        return "nacos://" + namespaceId + "/" + resourceType + "/" + resourceName + "/"
            + version;
    }
    
    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
    
    private enum ResourceKind {
        
        SKILL(Constants.Skills.RESOURCE_TYPE_SKILL, MEDIA_TYPE_SKILL),
        
        PROMPT(NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, MEDIA_TYPE_PROMPT),
        
        MCP(RESOURCE_TYPE_MCP, MEDIA_TYPE_MCP);
        
        private final String resourceType;
        
        private final String mediaType;
        
        ResourceKind(String resourceType, String mediaType) {
            this.resourceType = resourceType;
            this.mediaType = mediaType;
        }
    }
    
    private static class SearchContext {
        
        private String namespaceId;
        
        private String text;
        
        private String normalizedText;
        
        private Map<String, List<String>> filter;
        
        private List<ResourceKind> kinds;
        
        private int pageSize;
    }
}
