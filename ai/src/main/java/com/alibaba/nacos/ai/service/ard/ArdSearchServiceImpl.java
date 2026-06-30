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
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.model.ard.ArdSearchHit;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.ard.vector.AiResourceVectorIndex;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchQuery;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
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
 * Nacos Local ARD Search implementation backed by ARD Entry, Chunk and vector indexes.
 *
 * @author nacos
 */
@Service
public class ArdSearchServiceImpl implements ArdSearchService {
    
    static final String FEDERATION_NONE = "none";
    
    private static final int DEFAULT_PAGE_SIZE = 10;
    
    private static final int MAX_PAGE_SIZE = 50;
    
    private static final int MAX_CHUNK_CANDIDATES = 500;
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final Set<String> SUPPORTED_FILTER_KEYS =
        new LinkedHashSet<>(Arrays.asList("type", "tags", "capabilities",
            "metadata.resourceType", "metadata.inputTypes", "metadata.outputTypes",
            "metadata.sideEffects", "metadata.riskLevel"));
    
    private final AiResourceManager resourceManager;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final ArdIndexRepository repository;
    
    private final ArdEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    public ArdSearchServiceImpl(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService, ArdIndexRepository repository,
        ArdEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex) {
        this.resourceManager = resourceManager;
        this.mcpServerOperationService = mcpServerOperationService;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
    }
    
    @Override
    public ArdSearchResponse search(ArdSearchRequest request) throws NacosException {
        SearchContext context = validateAndBuildContext(request);
        Map<Long, Double> scores = recall(context);
        List<ArdEntry> entries = repository.findEntriesByIds(scores.keySet());
        List<ArdSearchResult> candidates = new ArrayList<>();
        for (ArdEntry entry : entries) {
            if (!matchesFieldFilters(context.filter, entry) || !validateCurrentResource(entry)) {
                continue;
            }
            ArdSearchResult result = toResult(entry);
            result.setScore(scores.get(entry.getId()));
            candidates.add(result);
        }
        candidates.sort(Comparator.comparing(ArdSearchResult::getScore,
            Comparator.nullsLast(Comparator.reverseOrder())));
        ArdSearchResponse response = new ArdSearchResponse();
        response.setResults(limit(candidates, context.pageSize));
        response.setReferrals(Collections.emptyList());
        return response;
    }
    
    private Map<Long, Double> recall(SearchContext context) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        int candidateLimit = Math.max(MAX_CHUNK_CANDIDATES, context.pageSize * 20);
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(context.text);
            for (ArdSearchHit hit : vectorIndex.search(context.namespaceId, vector,
                context.resourceTypes, candidateLimit)) {
                recordScore(scores, hit);
            }
        }
        for (ArdSearchHit hit : repository.searchChunks(context.namespaceId, context.text,
            context.resourceTypes, candidateLimit)) {
            recordScore(scores, hit);
        }
        return scores;
    }
    
    private void recordScore(Map<Long, Double> scores, ArdSearchHit hit) {
        if (hit == null || hit.getEntryId() == null) {
            return;
        }
        scores.merge(hit.getEntryId(), hit.getScore(), Math::max);
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
        context.filter = filter;
        context.pageSize = normalizePageSize(request.getPageSize());
        context.kinds = resolveKinds(filter);
        context.resourceTypes = resourceTypes(context.kinds);
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
    
    private List<String> resourceTypes(List<ResourceKind> kinds) {
        List<String> result = new ArrayList<>();
        for (ResourceKind kind : kinds) {
            result.add(kind.resourceType);
        }
        return result;
    }
    
    private boolean matchesFieldFilters(Map<String, List<String>> filter, ArdEntry entry) {
        Map<String, Object> metadata = parseMap(entry.getMetadata());
        return matchesAny(filter.get("tags"), parseStringList(entry.getTags()))
            && matchesAny(filter.get("capabilities"), parseStringList(entry.getCapabilities()))
            && matchesMetadata(filter.get("metadata.inputTypes"), metadata, "inputTypes")
            && matchesMetadata(filter.get("metadata.outputTypes"), metadata, "outputTypes")
            && matchesMetadata(filter.get("metadata.sideEffects"), metadata, "sideEffects")
            && matchesMetadata(filter.get("metadata.riskLevel"), metadata, "riskLevel");
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
    
    private boolean matchesMetadata(List<String> expected, Map<String, Object> metadata,
        String key) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        Object actual = metadata == null ? null : metadata.get(key);
        if (actual == null) {
            return false;
        }
        return matchesAny(expected, toStringList(actual));
    }
    
    private boolean validateCurrentResource(ArdEntry entry) throws NacosException {
        if (ResourceKind.MCP.resourceType.equals(entry.getResourceType())) {
            return validateMcp(entry);
        }
        return validateAiResource(entry);
    }
    
    private boolean validateAiResource(ArdEntry entry) throws NacosException {
        AiResource meta = resourceManager.findMeta(entry.getNamespaceId(), entry.getResourceName(),
            entry.getResourceType());
        if (meta == null
            || !AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            return false;
        }
        try {
            resourceManager.ensureReadableOrNotFound(meta,
                entry.getResourceType() + " not found: " + entry.getResourceName());
        } catch (NacosException e) {
            return false;
        }
        String latestVersion = AiResourceManager.resolveVersion(meta, null,
            AiResourceConstants.LABEL_LATEST);
        if (!entry.getResourceVersion().equals(latestVersion)) {
            return false;
        }
        AiResourceVersion version = resourceManager.findVersion(entry.getNamespaceId(),
            entry.getResourceName(), entry.getResourceType(), entry.getResourceVersion());
        return version != null
            && AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus());
    }
    
    private boolean validateMcp(ArdEntry entry) throws NacosException {
        Map<String, Object> metadata = parseMap(entry.getMetadata());
        String mcpServerId = firstNotBlank(stringValue(metadata.get("mcpServerId")),
            entry.getResourceName());
        String mcpName = stringValue(metadata.get("mcpName"));
        try {
            McpServerDetailInfo detail = mcpServerOperationService.getMcpServerDetail(
                entry.getNamespaceId(), mcpServerId, mcpName, entry.getResourceVersion());
            ServerVersionDetail versionDetail = detail.getVersionDetail();
            return detail.isEnabled()
                && AiConstants.Mcp.MCP_STATUS_ACTIVE.equalsIgnoreCase(detail.getStatus())
                && versionDetail != null && Boolean.TRUE.equals(versionDetail.getIs_latest());
        } catch (NacosException e) {
            return false;
        }
    }
    
    private ArdSearchResult toResult(ArdEntry entry) {
        ArdSearchResult result = new ArdSearchResult();
        result.setIdentifier(entry.getIdentifier());
        result.setDisplayName(entry.getDisplayName());
        result.setType(entry.getType());
        result.setUrl(entry.getUrl());
        result.setDescription(entry.getDescription());
        result.setTags(parseStringList(entry.getTags()));
        result.setCapabilities(parseStringList(entry.getCapabilities()));
        result.setRepresentativeQueries(parseStringList(entry.getRepresentativeQueries()));
        result.setVersion(entry.getResourceVersion());
        result.setUpdatedAt(formatTimestamp(entry.getGmtModified()));
        result.setMetadata(parseMap(entry.getMetadata()));
        result.setSource(entry.getSource());
        return result;
    }
    
    private List<String> parseStringList(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = JacksonUtils.toObj(value, STRING_LIST_TYPE);
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception ignored) {
            return Collections.singletonList(value);
        }
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
    
    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
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
        return Collections.singletonList(String.valueOf(value));
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
    
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
    
    private List<ArdSearchResult> limit(List<ArdSearchResult> candidates, int pageSize) {
        if (candidates.size() <= pageSize) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, pageSize));
    }
    
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
    
    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
    
    private enum ResourceKind {
        
        SKILL(Constants.Skills.RESOURCE_TYPE_SKILL, ArdIndexConstants.MEDIA_TYPE_SKILL),
        
        PROMPT(NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT,
            ArdIndexConstants.MEDIA_TYPE_PROMPT),
        
        MCP(ArdIndexConstants.RESOURCE_TYPE_MCP, ArdIndexConstants.MEDIA_TYPE_MCP);
        
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
        
        private Map<String, List<String>> filter;
        
        private List<ResourceKind> kinds;
        
        private List<String> resourceTypes;
        
        private int pageSize;
    }
}
