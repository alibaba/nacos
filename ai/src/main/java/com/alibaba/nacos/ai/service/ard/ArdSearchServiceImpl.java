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
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
    
    private static final int RRF_K = 60;
    
    private static final double RRF_SCORE_SCALE = 100.0D;
    
    private static final double KEYWORD_RRF_WEIGHT = 1.0D;
    
    private static final double VECTOR_RRF_WEIGHT = 0.6D;
    
    private static final String KEY_RANKING_ENHANCED_ENABLED =
        "nacos.ai.ard.search.ranking.enhanced.enabled";
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final String PAGE_TOKEN_OFFSET = "offset";
    
    private static final Set<String> SUPPORTED_FILTER_KEYS =
        new LinkedHashSet<>(Arrays.asList("type", "tags", "capabilities",
            "metadata.resourceType", "metadata.inputTypes", "metadata.outputTypes",
            "metadata.sideEffects", "metadata.riskLevel"));
    
    private static final Map<String, Double> CHUNK_TYPE_WEIGHTS = chunkTypeWeights();
    
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
        boolean enhancedRanking = enhancedRankingEnabled();
        Map<Long, SearchScore> scores = recall(context, enhancedRanking);
        List<ArdEntry> entries = repository.findEntriesByIds(scores.keySet());
        List<RankedResult> candidates = new ArrayList<>();
        for (ArdEntry entry : entries) {
            if (!matchesFieldFilters(context.filter, entry) || !validateCurrentResource(entry)) {
                continue;
            }
            ArdSearchResult result = toResult(entry);
            SearchScore score = scores.get(entry.getId());
            double finalScore = score == null ? 0D : score.getScore();
            if (enhancedRanking) {
                finalScore += exactMatchBoost(entry, context.text);
            }
            result.setScore(finalScore);
            candidates.add(new RankedResult(result, entry.getId(), entry.getGmtModified()));
        }
        candidates.sort(resultComparator());
        return page(toResults(candidates), context);
    }
    
    private Map<Long, SearchScore> recall(SearchContext context, boolean enhancedRanking) {
        if (!enhancedRanking) {
            return recallWithMaxScore(context);
        }
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        int candidateLimit = Math.max(MAX_CHUNK_CANDIDATES, context.pageSize * 20);
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(context.text);
            recordRrfScores(scores, sortHitsByScore(vectorIndex.search(context.namespaceId, vector,
                context.resourceTypes, candidateLimit), false), VECTOR_RRF_WEIGHT, false);
        }
        recordRrfScores(scores, sortHitsByScore(repository.searchChunks(context.namespaceId,
            context.text, context.resourceTypes, candidateLimit), true), KEYWORD_RRF_WEIGHT,
            true);
        return scores;
    }
    
    private Map<Long, SearchScore> recallWithMaxScore(SearchContext context) {
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        int candidateLimit = Math.max(MAX_CHUNK_CANDIDATES, context.pageSize * 20);
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(context.text);
            for (ArdSearchHit hit : vectorIndex.search(context.namespaceId, vector,
                context.resourceTypes, candidateLimit)) {
                recordMaxScore(scores, hit);
            }
        }
        for (ArdSearchHit hit : repository.searchChunks(context.namespaceId, context.text,
            context.resourceTypes, candidateLimit)) {
            recordMaxScore(scores, hit);
        }
        return scores;
    }
    
    private List<ArdSearchHit> sortHitsByScore(List<ArdSearchHit> hits, boolean useChunkWeight) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArdSearchHit> result = new ArrayList<>(hits);
        result.sort(Comparator.comparing((ArdSearchHit hit) -> hitScore(hit, useChunkWeight))
            .reversed().thenComparing(ArdSearchHit::getEntryId,
                Comparator.nullsLast(Long::compareTo))
            .thenComparing(ArdSearchHit::getChunkId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }
    
    private double hitScore(ArdSearchHit hit, boolean useChunkWeight) {
        if (hit == null) {
            return 0D;
        }
        if (!useChunkWeight) {
            return hit.getScore();
        }
        return hit.getScore() * chunkTypeWeight(hit.getChunkType());
    }
    
    private void recordRrfScores(Map<Long, SearchScore> scores, List<ArdSearchHit> hits,
        double channelWeight, boolean useChunkWeight) {
        Set<Long> seenEntries = new LinkedHashSet<>();
        int rank = 0;
        for (ArdSearchHit hit : hits) {
            if (hit == null || hit.getEntryId() == null || !seenEntries.add(hit.getEntryId())) {
                continue;
            }
            rank++;
            double chunkWeight = useChunkWeight ? chunkTypeWeight(hit.getChunkType()) : 1.0D;
            double score = RRF_SCORE_SCALE * channelWeight * chunkWeight / (RRF_K + rank);
            scores.computeIfAbsent(hit.getEntryId(), key -> new SearchScore()).add(score);
        }
    }
    
    private void recordMaxScore(Map<Long, SearchScore> scores, ArdSearchHit hit) {
        if (hit == null || hit.getEntryId() == null) {
            return;
        }
        scores.computeIfAbsent(hit.getEntryId(), key -> new SearchScore())
            .max(hit.getScore());
    }
    
    private double chunkTypeWeight(String chunkType) {
        Double weight = CHUNK_TYPE_WEIGHTS.get(chunkType);
        return weight == null ? 1.0D : weight;
    }
    
    private double exactMatchBoost(ArdEntry entry, String query) {
        String normalizedQuery = normalize(query);
        String compactQuery = compact(query);
        double identityBoost = Math.max(identityBoost(entry.getResourceName(), normalizedQuery,
            compactQuery), identityBoost(entry.getDisplayName(), normalizedQuery, compactQuery));
        if (containsNormalized(entry.getIdentifier(), normalizedQuery)) {
            identityBoost = Math.max(identityBoost, 1.2D);
        }
        double listBoost = 0D;
        if (containsIgnoreCase(parseStringList(entry.getTags()), query)
            || containsIgnoreCase(parseStringList(entry.getCapabilities()), query)) {
            listBoost = 1.0D;
        }
        double queryBoost = containsNormalized(parseStringList(entry.getRepresentativeQueries()),
            normalizedQuery) ? 0.8D : 0D;
        return identityBoost + listBoost + queryBoost;
    }
    
    private double identityBoost(String value, String normalizedQuery, String compactQuery) {
        String normalizedValue = normalize(value);
        if (StringUtils.isBlank(normalizedValue) || StringUtils.isBlank(normalizedQuery)) {
            return 0D;
        }
        if (normalizedValue.equals(normalizedQuery)) {
            return 3.0D;
        }
        if (StringUtils.isNotBlank(compactQuery) && compact(value).equals(compactQuery)) {
            return 2.5D;
        }
        return normalizedValue.contains(normalizedQuery) ? 1.5D : 0D;
    }
    
    private boolean containsNormalized(String value, String normalizedQuery) {
        return StringUtils.isNotBlank(value) && StringUtils.isNotBlank(normalizedQuery)
            && normalize(value).contains(normalizedQuery);
    }
    
    private boolean containsNormalized(List<String> values, String normalizedQuery) {
        if (values == null || StringUtils.isBlank(normalizedQuery)) {
            return false;
        }
        for (String value : values) {
            if (containsNormalized(value, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }
    
    private List<ArdSearchResult> toResults(List<RankedResult> rankedResults) {
        List<ArdSearchResult> results = new ArrayList<>();
        for (RankedResult rankedResult : rankedResults) {
            results.add(rankedResult.result);
        }
        return results;
    }
    
    private Comparator<RankedResult> resultComparator() {
        return Comparator.comparing((RankedResult result) -> result.result.getScore(),
            Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing((RankedResult result) -> result.gmtModified,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(result -> result.entryId, Comparator.nullsLast(Long::compareTo));
    }
    
    private boolean enhancedRankingEnabled() {
        return Boolean.parseBoolean(property(KEY_RANKING_ENHANCED_ENABLED, "true"));
    }
    
    private String property(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        try {
            return EnvUtil.getProperty(key, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
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
        context.pageOffset = decodePageOffset(request.getPageToken());
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
    
    private int decodePageOffset(String pageToken) throws NacosApiException {
        if (StringUtils.isBlank(pageToken)) {
            return 0;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(pageToken);
            Map<String, Object> token =
                JacksonUtils.toObj(new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
            Object offsetValue = token == null ? null : token.get(PAGE_TOKEN_OFFSET);
            int offset = parseOffset(offsetValue);
            if (offset >= 0) {
                return offset;
            }
        } catch (Exception ignored) {
            // Fall through to a typed API error below.
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid ARD pageToken");
    }
    
    private int parseOffset(Object offsetValue) {
        if (offsetValue instanceof Number) {
            return ((Number) offsetValue).intValue();
        }
        if (offsetValue instanceof String && StringUtils.isNotBlank((String) offsetValue)) {
            return Integer.parseInt((String) offsetValue);
        }
        return -1;
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
    
    private String compact(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                result.append(Character.toLowerCase(ch));
            }
        }
        return result.toString();
    }
    
    private ArdSearchResponse page(List<ArdSearchResult> candidates, SearchContext context) {
        ArdSearchResponse response = new ArdSearchResponse();
        response.setReferrals(Collections.emptyList());
        int fromIndex = Math.min(context.pageOffset, candidates.size());
        int toIndex = Math.min(fromIndex + context.pageSize, candidates.size());
        response.setResults(new ArrayList<>(candidates.subList(fromIndex, toIndex)));
        if (toIndex < candidates.size()) {
            response.setNextPageToken(encodePageToken(toIndex));
        }
        return response;
    }
    
    private String encodePageToken(int offset) {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put(PAGE_TOKEN_OFFSET, offset);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            JacksonUtils.toJson(token).getBytes(StandardCharsets.UTF_8));
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
        
        private int pageOffset;
    }
    
    private static class SearchScore {
        
        private double score;
        
        private void add(double value) {
            score += value;
        }
        
        private void max(double value) {
            score = Math.max(score, value);
        }
        
        private double getScore() {
            return score;
        }
    }
    
    private static class RankedResult {
        
        private final ArdSearchResult result;
        
        private final Long entryId;
        
        private final Timestamp gmtModified;
        
        private RankedResult(ArdSearchResult result, Long entryId, Timestamp gmtModified) {
            this.result = result;
            this.entryId = entryId;
            this.gmtModified = gmtModified;
        }
    }
    
    private static Map<String, Double> chunkTypeWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(ArdIndexConstants.CHUNK_TYPE_EXAMPLE_QUERY, 1.8D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_BILINGUAL_ALIAS, 1.7D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_CAPABILITY_SYNONYM, 1.5D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_REPRESENTATIVE_QUERY, 1.5D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_CAPABILITY, 1.3D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_DESCRIPTION, 1.1D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_TAG, 1.0D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_AI_SUMMARY, 1.0D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_SKILL_CONTENT, 0.7D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_METADATA_IO, 0.6D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_METADATA_RISK, 0.5D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_NOT_FOR, 0.4D);
        return Collections.unmodifiableMap(weights);
    }
}
