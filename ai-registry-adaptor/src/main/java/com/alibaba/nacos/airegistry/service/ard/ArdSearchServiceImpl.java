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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.ai.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.model.ard.ArdSearchHit;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.ard.ArdEmbeddingService;
import com.alibaba.nacos.ai.service.ard.ArdIndexConstants;
import com.alibaba.nacos.ai.service.ard.ArdIndexRepository;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalogEntry;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResultType;
import com.alibaba.nacos.airegistry.model.ard.ArdFacetRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdHostInfo;
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchFilter;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchQuery;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResult;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
@ConditionalOnArdEnabled
public class ArdSearchServiceImpl implements ArdSearchService {
    
    private static final int DEFAULT_PAGE_SIZE = 10;
    
    private static final int MAX_PAGE_SIZE = 50;
    
    private static final int DEFAULT_LIST_PAGE_SIZE = 20;
    
    private static final int MAX_LIST_PAGE_SIZE = 100;
    
    private static final int DEFAULT_FACET_LIMIT = 20;
    
    private static final int MAX_LIST_CANDIDATES = 1000;
    
    private static final int MAX_CHUNK_CANDIDATES = 500;
    
    private static final int RRF_K = 60;
    
    private static final double RRF_SCORE_SCALE = 100.0D;
    
    private static final double KEYWORD_RRF_WEIGHT = 1.0D;
    
    private static final double VECTOR_RRF_WEIGHT = 0.6D;
    
    private static final String KEY_RANKING_ENHANCED_ENABLED =
        "nacos.ai.ard.search.ranking.enhanced.enabled";
    
    private static final String KEY_CATALOG_BASE_URL = "nacos.ai.ard.catalog.base-url";
    
    private static final String KEY_CATALOG_HOST_DISPLAY_NAME =
        "nacos.ai.ard.catalog.host.display-name";
    
    private static final String KEY_CATALOG_HOST_DOCUMENTATION_URL =
        "nacos.ai.ard.catalog.host.documentation-url";
    
    private static final String KEY_CATALOG_TRUST_IDENTITY =
        "nacos.ai.ard.catalog.trust.identity";
    
    private static final String KEY_CATALOG_TRUST_IDENTITY_TYPE =
        "nacos.ai.ard.catalog.trust.identity-type";
    
    private static final String KEY_CATALOG_MAX_ENTRIES = "nacos.ai.ard.catalog.max-entries";
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final String PAGE_TOKEN_OFFSET = "offset";
    
    private static final Set<String> SUPPORTED_FILTER_KEYS =
        new LinkedHashSet<>(Arrays.asList("displayName", "type", "publisher",
            "publisherId", "version", "source", "tags", "capabilities",
            "representativeQueries", "metadata.resourceType", "metadata.inputTypes",
            "metadata.outputTypes", "metadata.sideEffects", "metadata.riskLevel",
            "metadata.scope", "trustManifest.identity", "trustManifest.identityType"));
    
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
            candidates.add(new RankedResult(result, entry.getId(), entry.getGmtModified(),
                finalScore));
        }
        candidates.sort(resultComparator());
        normalizeScores(candidates);
        return page(toResults(candidates), context);
    }
    
    @Override
    public ArdExploreResponse explore(ArdExploreRequest request) throws NacosException {
        ExploreContext context = validateAndBuildExploreContext(request);
        List<ArdSearchResult> results = matchedResults(context);
        ArdExploreResponse response = new ArdExploreResponse();
        Map<String, ArdExploreResponse.FacetResult> facets = new LinkedHashMap<>();
        for (ArdFacetRequest facetRequest : context.facets) {
            facets.put(facetRequest.getField(), facet(results, facetRequest));
        }
        response.setFacets(facets);
        return response;
    }
    
    @Override
    public ArdListResponse list(String namespaceId, String filter, String orderBy,
        Integer pageSize, String pageToken) throws NacosException {
        ListContext context = validateAndBuildListContext(namespaceId, filter, orderBy, pageSize,
            pageToken);
        List<ArdCatalogEntry> results = new ArrayList<>();
        for (ArdEntry entry : matchedEntries(context)) {
            ArdCatalogEntry result = toCatalogEntry(entry);
            if (matchesListFilters(context, result)) {
                results.add(result);
            }
        }
        results.sort(listComparator(context.orderBy, context.orderDescending));
        return listPage(results, context.pageSize, context.pageOffset);
    }
    
    @Override
    public ArdCatalog hostCatalog() {
        ArdCatalog catalog = new ArdCatalog();
        catalog.setSpecVersion(ArdProtocolConstants.SPEC_VERSION);
        catalog.setHost(hostInfo());
        catalog.setEntries(Collections.singletonList(registryEntry()));
        return catalog;
    }
    
    @Override
    public ArdCatalog catalog(String namespaceId) throws NacosException {
        String resolvedNamespace = normalizeNamespaceId(namespaceId);
        ArdCatalog catalog = new ArdCatalog();
        catalog.setSpecVersion(ArdProtocolConstants.SPEC_VERSION);
        catalog.setHost(hostInfo());
        List<ArdCatalogEntry> entries = new ArrayList<>();
        entries.add(registryEntry());
        for (ArdEntry entry : currentEntries(resolvedNamespace, allResourceTypes(),
            positiveInt(KEY_CATALOG_MAX_ENTRIES, 100))) {
            entries.add(toCatalogEntry(entry));
        }
        catalog.setEntries(entries);
        return catalog;
    }
    
    private Map<Long, SearchScore> recall(SearchContext context, boolean enhancedRanking) {
        if (!enhancedRanking) {
            return recallWithMaxScore(context);
        }
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        int candidateLimit = Math.max(MAX_CHUNK_CANDIDATES, context.pageSize * 20);
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(context.text);
            recordRrfScores(scores, sortHitsByScore(toSearchHits(vectorIndex.search(
                context.namespaceId, embeddingService.model(), vector, context.resourceTypes,
                candidateLimit)), false), VECTOR_RRF_WEIGHT, false);
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
            for (ArdSearchHit hit : toSearchHits(vectorIndex.search(context.namespaceId,
                embeddingService.model(), vector, context.resourceTypes, candidateLimit))) {
                recordMaxScore(scores, hit);
            }
        }
        for (ArdSearchHit hit : repository.searchChunks(context.namespaceId, context.text,
            context.resourceTypes, candidateLimit)) {
            recordMaxScore(scores, hit);
        }
        return scores;
    }
    
    private List<ArdSearchResult> matchedResults(SearchContext context) throws NacosException {
        List<ArdSearchResult> results = new ArrayList<>();
        for (ArdEntry entry : matchedEntries(context)) {
            results.add(toResult(entry));
        }
        return results;
    }
    
    private List<ArdEntry> matchedEntries(SearchContext context) throws NacosException {
        List<ArdEntry> entries;
        if (StringUtils.isBlank(context.text)) {
            entries = repository.listEnabledEntries(context.namespaceId, context.resourceTypes,
                MAX_LIST_CANDIDATES);
        } else {
            Map<Long, SearchScore> scores = recall(context, enhancedRankingEnabled());
            entries = repository.findEntriesByIds(scores.keySet());
        }
        return filterCurrentEntries(entries, context.filter);
    }
    
    private List<ArdEntry> currentEntries(String namespaceId, List<String> resourceTypes,
        int limit) throws NacosException {
        return filterCurrentEntries(repository.listEnabledEntries(namespaceId, resourceTypes,
            limit), Collections.emptyMap());
    }
    
    private List<ArdEntry> filterCurrentEntries(List<ArdEntry> entries,
        Map<String, List<String>> filter) throws NacosException {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArdEntry> result = new ArrayList<>();
        for (ArdEntry entry : entries) {
            if (matchesFieldFilters(filter, entry) && validateCurrentResource(entry)) {
                result.add(entry);
            }
        }
        return result;
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
    
    private List<ArdSearchHit> toSearchHits(List<AiResourceVectorHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArdSearchHit> result = new ArrayList<>(hits.size());
        for (AiResourceVectorHit hit : hits) {
            if (hit == null) {
                continue;
            }
            ArdSearchHit converted = new ArdSearchHit();
            converted.setEntryId(hit.getEntryId());
            converted.setChunkId(hit.getChunkId());
            converted.setResourceType(hit.getResourceType());
            converted.setResourceName(hit.getResourceName());
            converted.setResourceVersion(hit.getResourceVersion());
            converted.setChunkType(hit.getChunkType());
            converted.setScore(hit.getScore());
            result.add(converted);
        }
        return result;
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
        if (containsNormalized(buildIdentifier(entry), normalizedQuery)) {
            identityBoost = Math.max(identityBoost, 1.2D);
        }
        double listBoost = 0D;
        if (equalsIgnoreCase(parseStringList(entry.getTags()), query)
            || equalsIgnoreCase(parseStringList(entry.getCapabilities()), query)) {
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
    
    private void normalizeScores(List<RankedResult> candidates) {
        double maxScore = 0D;
        for (RankedResult candidate : candidates) {
            maxScore = Math.max(maxScore, candidate.rankScore);
        }
        for (RankedResult candidate : candidates) {
            candidate.result.setScore(normalizeScore(candidate.rankScore, maxScore));
        }
    }
    
    private int normalizeScore(double score, double maxScore) {
        if (score <= 0D || maxScore <= 0D) {
            return 0;
        }
        return (int) Math.min(100L, Math.round(score * 100D / maxScore));
    }
    
    private Comparator<RankedResult> resultComparator() {
        return Comparator.comparingDouble((RankedResult result) -> result.rankScore).reversed()
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
        String federation = StringUtils.isBlank(request.getFederation())
            ? ArdProtocolConstants.FEDERATION_NONE : request.getFederation();
        if (!ArdProtocolConstants.FEDERATION_NONE.equalsIgnoreCase(federation)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Only federation `none` is supported by Nacos Local ARD Search");
        }
        ArdSearchQuery query = request.getQuery();
        Map<String, List<String>> filter = normalizeFilter(query);
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
    
    private ExploreContext validateAndBuildExploreContext(ArdExploreRequest request)
        throws NacosApiException {
        if (request == null || request.getResultType() == null
            || request.getResultType().getFacets() == null
            || request.getResultType().getFacets().isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Required parameter `resultType.facets` not present");
        }
        ArdSearchQuery query = request.getQuery() == null ? new ArdSearchQuery()
            : request.getQuery();
        Map<String, List<String>> filter = normalizeFilter(query);
        validateFilterKeys(filter.keySet());
        ArdExploreResultType resultType = request.getResultType();
        ExploreContext context = new ExploreContext();
        context.namespaceId = normalizeNamespaceId(request.getNamespaceId());
        context.text = StringUtils.isBlank(query.getText()) ? null : query.getText().trim();
        context.filter = filter;
        context.pageSize = MAX_PAGE_SIZE;
        context.pageOffset = 0;
        context.kinds = resolveKinds(filter);
        context.resourceTypes = resourceTypes(context.kinds);
        context.facets = normalizeFacets(resultType.getFacets());
        return context;
    }
    
    private ListContext validateAndBuildListContext(String namespaceId, String filter,
        String orderBy, Integer pageSize, String pageToken) throws NacosApiException {
        ListContext context = new ListContext();
        context.namespaceId = normalizeNamespaceId(namespaceId);
        context.filter = normalizeListFilter(filter, context);
        validateFilterKeys(context.filter.keySet());
        context.kinds = resolveKinds(context.filter);
        context.resourceTypes = resourceTypes(context.kinds);
        context.pageSize = normalizeListPageSize(pageSize);
        context.pageOffset = decodePageOffset(pageToken);
        parseOrderBy(orderBy, context);
        return context;
    }
    
    private String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId)
            ? com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    private List<ArdFacetRequest> normalizeFacets(List<ArdFacetRequest> facets)
        throws NacosApiException {
        List<ArdFacetRequest> result = new ArrayList<>();
        for (ArdFacetRequest facet : facets) {
            if (facet == null || StringUtils.isBlank(facet.getField())) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `resultType.facets.field` not present");
            }
            String field = facet.getField().trim();
            validateFilterKeys(Collections.singleton(field));
            ArdFacetRequest normalized = new ArdFacetRequest();
            normalized.setField(field);
            normalized.setLimit(facetLimit(facet.getLimit()));
            normalized.setMinCount(facet.getMinCount() == null ? 1
                : Math.max(1, facet.getMinCount()));
            result.add(normalized);
        }
        return result;
    }
    
    private int facetLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_FACET_LIMIT;
        }
        return Math.min(limit, MAX_LIST_PAGE_SIZE);
    }
    
    private Map<String, List<String>> normalizeListFilter(String filter,
        ListContext context) throws NacosApiException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(filter)) {
            return result;
        }
        for (String expression : filter.split("[;&]")) {
            if (StringUtils.isBlank(expression)) {
                continue;
            }
            int separator = filterSeparator(expression);
            if (separator <= 0) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Invalid ARD list filter expression: " + expression);
            }
            String field = expression.substring(0, separator).trim();
            String value = expression.substring(separator + 1).trim();
            if ("createdAfter".equals(field)) {
                context.createdAfter = parseInstant(field, value);
                continue;
            }
            if ("updatedAfter".equals(field)) {
                context.updatedAfter = parseInstant(field, value);
                continue;
            }
            addFilter(result, field, commaSeparatedValues(field, value));
        }
        return result;
    }
    
    private int filterSeparator(String expression) {
        int equals = expression.indexOf('=');
        int colon = expression.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }
    
    private List<String> commaSeparatedValues(String field, String value)
        throws NacosApiException {
        if (StringUtils.isBlank(value)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "ARD list filter `" + field + "` should not be blank");
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            if (StringUtils.isNotBlank(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }
    
    private Instant parseInstant(String field, String value) throws NacosApiException {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e,
                "ARD list filter `" + field + "` should be ISO-8601 timestamp");
        }
    }
    
    private int normalizeListPageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_LIST_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_LIST_PAGE_SIZE);
    }
    
    private void parseOrderBy(String orderBy, ListContext context) throws NacosApiException {
        context.orderBy = "updatedAt";
        context.orderDescending = true;
        if (StringUtils.isBlank(orderBy)) {
            return;
        }
        String[] parts = orderBy.trim().split("\\s+");
        String field = parts[0];
        if ("name".equalsIgnoreCase(field)) {
            field = "displayName";
        } else if ("updated_at".equalsIgnoreCase(field)) {
            field = "updatedAt";
        }
        if (!"displayName".equals(field) && !"updatedAt".equals(field)
            && !"identifier".equals(field)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Unsupported ARD orderBy field: " + field);
        }
        context.orderBy = field;
        context.orderDescending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
    }
    
    private Map<String, List<String>> normalizeFilter(ArdSearchQuery query)
        throws NacosApiException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (query.getFilter() != null && !query.getFilter().isEmpty()) {
            for (Map.Entry<String, Object> entry : query.getFilter().entrySet()) {
                addFilter(result, entry.getKey(),
                    normalizeFilterValues(entry.getKey(), entry.getValue()));
            }
        }
        if (query.getFilters() == null || query.getFilters().isEmpty()) {
            return result;
        }
        for (ArdSearchFilter filter : query.getFilters()) {
            if (filter == null) {
                continue;
            }
            String fieldPath = filter.getFieldPath();
            if (StringUtils.isBlank(fieldPath)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `query.filters.fieldPath` not present");
            }
            Object value = filter.getValues() == null ? filter.getValue() : filter.getValues();
            addFilter(result, fieldPath, normalizeFilterValues(fieldPath, value));
        }
        return result;
    }
    
    private void addFilter(Map<String, List<String>> result, String fieldPath,
        List<String> values) {
        result.computeIfAbsent(fieldPath, key -> new ArrayList<>()).addAll(values);
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
        if (mediaTypes != null && !equalsIgnoreCase(mediaTypes, kind.mediaType)) {
            return false;
        }
        return resourceTypes == null || equalsIgnoreCase(resourceTypes, kind.resourceType);
    }
    
    private List<String> resourceTypes(List<ResourceKind> kinds) {
        List<String> result = new ArrayList<>();
        for (ResourceKind kind : kinds) {
            result.add(kind.resourceType);
        }
        return result;
    }
    
    private boolean matchesFieldFilters(Map<String, List<String>> filter, ArdEntry entry) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, List<String>> each : filter.entrySet()) {
            if (!matchesAny(each.getValue(), fieldValues(entry, each.getKey()),
                "displayName".equals(each.getKey()))) {
                return false;
            }
        }
        return true;
    }
    
    private boolean matchesListFilters(ListContext context, ArdCatalogEntry result) {
        if (context.createdAfter != null && !isAfter(result.getMetadata().get("createdAt"),
            context.createdAfter)) {
            return false;
        }
        if (context.updatedAfter != null && !isAfter(result.getUpdatedAt(),
            context.updatedAfter)) {
            return false;
        }
        if (context.filter == null || context.filter.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, List<String>> each : context.filter.entrySet()) {
            if (!matchesAny(each.getValue(), fieldValues(result, each.getKey()),
                "displayName".equals(each.getKey()))) {
                return false;
            }
        }
        return true;
    }
    
    private boolean matchesAny(List<String> expected, List<String> actual, boolean contains) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        for (String eachExpected : expected) {
            if (contains ? containsIgnoreCase(actual, eachExpected)
                : equalsIgnoreCase(actual, eachExpected)) {
                return true;
            }
        }
        return false;
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
    
    private ArdCatalogEntry toCatalogEntry(ArdEntry entry) {
        ArdCatalogEntry result = new ArdCatalogEntry();
        populateCatalogEntry(result, entry);
        return result;
    }
    
    private ArdSearchResult toResult(ArdEntry entry) {
        ArdSearchResult result = new ArdSearchResult();
        populateCatalogEntry(result, entry);
        result.setSource(sourceUri());
        return result;
    }
    
    private void populateCatalogEntry(ArdCatalogEntry result, ArdEntry entry) {
        result.setIdentifier(buildIdentifier(entry));
        result.setDisplayName(entry.getDisplayName());
        result.setType(resourceKind(entry.getResourceType()).mediaType);
        result.setUrl(withBaseUrl(buildResourceUrl(entry)));
        result.setDescription(entry.getDescription());
        result.setTags(parseStringList(entry.getTags()));
        result.setCapabilities(parseStringList(entry.getCapabilities()));
        List<String> representativeQueries =
            parseStringList(entry.getRepresentativeQueries());
        result.setRepresentativeQueries(representativeQueries.size() < 2 ? null
            : representativeQueries);
        result.setVersion(entry.getResourceVersion());
        result.setUpdatedAt(formatTimestamp(entry.getGmtModified()));
        Map<String, Object> metadata = protocolMetadata(parseMap(entry.getMetadata()));
        if (entry.getGmtCreate() != null) {
            metadata.put("createdAt", formatTimestamp(entry.getGmtCreate()));
        }
        result.setMetadata(metadata);
        result.setTrustManifest(trustManifest());
    }
    
    private ArdHostInfo hostInfo() {
        ArdHostInfo host = new ArdHostInfo();
        host.setDisplayName(property(KEY_CATALOG_HOST_DISPLAY_NAME, "Nacos AI Registry"));
        String identifier = catalogHostIdentifier();
        if (StringUtils.isNotBlank(identifier)) {
            host.setIdentifier(identifier);
        }
        String documentationUrl = property(KEY_CATALOG_HOST_DOCUMENTATION_URL, "");
        if (StringUtils.isNotBlank(documentationUrl)) {
            host.setDocumentationUrl(documentationUrl);
        }
        host.setTrustManifest(trustManifest());
        return host;
    }
    
    private ArdCatalogEntry registryEntry() {
        ArdCatalogEntry result = new ArdCatalogEntry();
        result.setIdentifier("urn:air:" + catalogHostIdentifier() + ":registry:nacos");
        result.setDisplayName(property(KEY_CATALOG_HOST_DISPLAY_NAME, "Nacos AI Registry"));
        result.setType(ArdProtocolConstants.MEDIA_TYPE_REGISTRY);
        result.setUrl(withBaseUrl(ArdProtocolConstants.CLIENT_PATH));
        result.setDescription("Nacos local AI Registry ARD search endpoint.");
        result.setTags(List.of("registry", "search", "dynamic"));
        result.setCapabilities(List.of("search", "explore", "list"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("searchEndpoint", endpoints().get("search"));
        metadata.put("exploreEndpoint", endpoints().get("explore"));
        metadata.put("listEndpoint", endpoints().get("agents"));
        metadata.put("artifactEndpoint", endpoints().get("artifacts"));
        metadata.put("resourceTypes", "skill,prompt,mcp");
        result.setMetadata(metadata);
        result.setTrustManifest(trustManifest());
        return result;
    }
    
    private Map<String, String> endpoints() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("search", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/search"));
        endpoints.put("explore", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/explore"));
        endpoints.put("agents", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/agents"));
        endpoints.put("artifacts", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/artifacts"));
        return endpoints;
    }
    
    private String withBaseUrl(String url) {
        if (StringUtils.isBlank(url) || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String baseUrl = configuredBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = currentRequestBaseUrl();
        }
        if (StringUtils.isBlank(baseUrl)) {
            return url;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return url.startsWith("/") ? base + url : base + "/" + url;
    }
    
    private String configuredBaseUrl() {
        String baseUrl = property(KEY_CATALOG_BASE_URL, "");
        if (StringUtils.isBlank(baseUrl)) {
            return "";
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        String contextPath = EnvUtil.getContextPath();
        return StringUtils.isBlank(contextPath) ? base : base + contextPath;
    }
    
    private String currentRequestBaseUrl() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return "";
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        return ServletUriComponentsBuilder.fromRequestUri(request)
            .replacePath(request.getContextPath())
            .replaceQuery(null)
            .build()
            .toUriString();
    }
    
    private String buildIdentifier(ArdEntry entry) {
        if (StringUtils.isBlank(entry.getNamespaceId())
            || StringUtils.isBlank(entry.getResourceType())
            || StringUtils.isBlank(entry.getResourceName())) {
            return null;
        }
        return "urn:air:" + catalogHostIdentifier() + ":" + entry.getNamespaceId() + ":"
            + entry.getResourceType() + ":" + entry.getResourceName();
    }
    
    private String buildResourceUrl(ArdEntry entry) {
        ResourceKind kind = resourceKind(entry.getResourceType());
        StringBuilder url = new StringBuilder(ArdProtocolConstants.CLIENT_PATH)
            .append("/artifacts?namespaceId=").append(encode(entry.getNamespaceId()))
            .append("&resourceType=").append(encode(entry.getResourceType()))
            .append("&resourceName=").append(encode(entry.getResourceName()))
            .append("&version=").append(encode(entry.getResourceVersion()));
        if (ResourceKind.MCP == kind) {
            String mcpName = stringValue(parseMap(entry.getMetadata()).get("mcpName"));
            if (StringUtils.isNotBlank(mcpName)) {
                url.append("&mcpName=").append(encode(mcpName));
            }
        }
        return url.toString();
    }
    
    private Map<String, Object> trustManifest() {
        String identity = property(KEY_CATALOG_TRUST_IDENTITY, "");
        if (StringUtils.isBlank(identity)) {
            return null;
        }
        Map<String, Object> trustManifest = new LinkedHashMap<>();
        trustManifest.put("identity", identity);
        String identityType = property(KEY_CATALOG_TRUST_IDENTITY_TYPE, "");
        if (Arrays.asList("spiffe", "did", "https", "other").contains(identityType)) {
            trustManifest.put("identityType", identityType);
        }
        return trustManifest;
    }
    
    private String sourceUri() {
        return withBaseUrl(ArdProtocolConstants.CLIENT_PATH);
    }
    
    private Map<String, Object> protocolMetadata(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
    
    private ResourceKind resourceKind(String resourceType) {
        for (ResourceKind kind : ResourceKind.values()) {
            if (kind.resourceType.equals(resourceType)) {
                return kind;
            }
        }
        throw new IllegalStateException("Unsupported ARD indexed resource type: " + resourceType);
    }
    
    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
    
    private String catalogHostIdentifier() {
        return property(ArdProtocolConstants.KEY_CATALOG_HOST_IDENTIFIER,
            ArdProtocolConstants.DEFAULT_CATALOG_HOST_IDENTIFIER);
    }
    
    private ArdExploreResponse.FacetResult facet(List<ArdSearchResult> results,
        ArdFacetRequest request) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ArdSearchResult result : results) {
            for (String value : fieldValues(result, request.getField())) {
                counts.put(value, counts.getOrDefault(value, 0) + 1);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= request.getMinCount()) {
                sorted.add(entry);
            }
        }
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
            .reversed().thenComparing(Map.Entry::getKey));
        ArdExploreResponse.FacetResult facet = new ArdExploreResponse.FacetResult();
        List<ArdExploreResponse.FacetBucket> buckets = new ArrayList<>();
        int otherCount = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            if (i < request.getLimit()) {
                ArdExploreResponse.FacetBucket bucket = new ArdExploreResponse.FacetBucket();
                bucket.setValue(entry.getKey());
                bucket.setCount(entry.getValue());
                buckets.add(bucket);
            } else {
                otherCount += entry.getValue();
            }
        }
        facet.setBuckets(buckets);
        facet.setOtherCount(otherCount);
        return facet;
    }
    
    private List<String> fieldValues(ArdEntry entry, String field) {
        if ("displayName".equals(field)) {
            return singleton(entry.getDisplayName());
        }
        if ("type".equals(field)) {
            return singleton(resourceKind(entry.getResourceType()).mediaType);
        }
        if ("publisher".equals(field) || "publisherId".equals(field)) {
            return singleton(publisher(buildIdentifier(entry)));
        }
        if ("version".equals(field)) {
            return singleton(entry.getResourceVersion());
        }
        if ("source".equals(field)) {
            return singleton(sourceUri());
        }
        if ("tags".equals(field)) {
            return parseStringList(entry.getTags());
        }
        if ("capabilities".equals(field)) {
            return parseStringList(entry.getCapabilities());
        }
        if ("representativeQueries".equals(field)) {
            return parseStringList(entry.getRepresentativeQueries());
        }
        if (field.startsWith("metadata.")) {
            return toStringList(parseMap(entry.getMetadata()).get(field.substring(9)));
        }
        if (field.startsWith("trustManifest.")) {
            Map<String, Object> trustManifest = trustManifest();
            return trustManifest == null ? Collections.emptyList()
                : toStringList(trustManifest.get(field.substring(14)));
        }
        return Collections.emptyList();
    }
    
    private List<String> fieldValues(ArdCatalogEntry result, String field) {
        if ("displayName".equals(field)) {
            return singleton(result.getDisplayName());
        }
        if ("type".equals(field)) {
            return singleton(result.getType());
        }
        if ("publisher".equals(field) || "publisherId".equals(field)) {
            return singleton(publisher(result.getIdentifier()));
        }
        if ("version".equals(field)) {
            return singleton(result.getVersion());
        }
        if ("source".equals(field)) {
            return singleton(sourceUri());
        }
        if ("tags".equals(field)) {
            return result.getTags();
        }
        if ("capabilities".equals(field)) {
            return result.getCapabilities();
        }
        if ("representativeQueries".equals(field)) {
            return result.getRepresentativeQueries();
        }
        if (field.startsWith("metadata.")) {
            return toStringList(result.getMetadata().get(field.substring(9)));
        }
        if (field.startsWith("trustManifest.")) {
            return result.getTrustManifest() == null ? Collections.emptyList()
                : toStringList(result.getTrustManifest().get(field.substring(14)));
        }
        return Collections.emptyList();
    }
    
    private List<String> singleton(String value) {
        return StringUtils.isBlank(value) ? Collections.emptyList()
            : Collections.singletonList(value);
    }
    
    private String publisher(String identifier) {
        if (StringUtils.isBlank(identifier) || !identifier.startsWith("urn:air:")) {
            return null;
        }
        String suffix = identifier.substring("urn:air:".length());
        int index = suffix.indexOf(':');
        return index < 0 ? suffix : suffix.substring(0, index);
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
    
    private boolean equalsIgnoreCase(List<String> values, String expected) {
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
    
    private boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null || StringUtils.isBlank(expected)) {
            return false;
        }
        String normalizedExpected = normalize(expected);
        for (String value : values) {
            if (normalize(value).contains(normalizedExpected)) {
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
            response.setPageToken(encodePageToken(toIndex));
        }
        return response;
    }
    
    private ArdListResponse listPage(List<ArdCatalogEntry> candidates, int pageSize,
        int pageOffset) {
        ArdListResponse response = new ArdListResponse();
        int fromIndex = Math.min(pageOffset, candidates.size());
        int toIndex = Math.min(fromIndex + pageSize, candidates.size());
        response.setItems(new ArrayList<>(candidates.subList(fromIndex, toIndex)));
        if (toIndex < candidates.size()) {
            response.setPageToken(encodePageToken(toIndex));
        }
        return response;
    }
    
    private Comparator<ArdCatalogEntry> listComparator(String orderBy, boolean descending) {
        Comparator<ArdCatalogEntry> comparator;
        if ("displayName".equals(orderBy)) {
            comparator = Comparator.comparing(ArdCatalogEntry::getDisplayName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else if ("identifier".equals(orderBy)) {
            comparator = Comparator.comparing(ArdCatalogEntry::getIdentifier,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else {
            comparator = Comparator.comparing(ArdCatalogEntry::getUpdatedAt,
                Comparator.nullsLast(String::compareTo));
        }
        if (descending) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(ArdCatalogEntry::getIdentifier,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }
    
    private String encodePageToken(int offset) {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put(PAGE_TOKEN_OFFSET, offset);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            JacksonUtils.toJson(token).getBytes(StandardCharsets.UTF_8));
    }
    
    private boolean isAfter(Object value, Instant threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        try {
            return Instant.parse(String.valueOf(value)).isAfter(threshold);
        } catch (Exception ignored) {
            return false;
        }
    }
    
    private List<String> allResourceTypes() {
        return resourceTypes(Arrays.asList(ResourceKind.values()));
    }
    
    private int positiveInt(String key, int defaultValue) {
        String value = property(key, String.valueOf(defaultValue));
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
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
        
        SKILL(AiResourceConstants.RESOURCE_TYPE_SKILL,
            ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE),
        
        PROMPT(AiResourceConstants.RESOURCE_TYPE_PROMPT, ArdProtocolConstants.MEDIA_TYPE_PROMPT),
        
        MCP(AiResourceConstants.RESOURCE_TYPE_MCP, ArdProtocolConstants.MEDIA_TYPE_MCP);
        
        private final String resourceType;
        
        private final String mediaType;
        
        ResourceKind(String resourceType, String mediaType) {
            this.resourceType = resourceType;
            this.mediaType = mediaType;
        }
    }
    
    private static class SearchContext {
        
        String namespaceId;
        
        String text;
        
        Map<String, List<String>> filter;
        
        List<ResourceKind> kinds;
        
        List<String> resourceTypes;
        
        int pageSize;
        
        int pageOffset;
    }
    
    private static class ExploreContext extends SearchContext {
        
        private List<ArdFacetRequest> facets;
    }
    
    private static class ListContext extends SearchContext {
        
        private String orderBy;
        
        private boolean orderDescending;
        
        private Instant createdAfter;
        
        private Instant updatedAfter;
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
        
        private final double rankScore;
        
        private RankedResult(ArdSearchResult result, Long entryId, Timestamp gmtModified,
            double rankScore) {
            this.result = result;
            this.entryId = entryId;
            this.gmtModified = gmtModified;
            this.rankScore = rankScore;
        }
    }
    
    private static Map<String, Double> chunkTypeWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(ArdIndexConstants.CHUNK_TYPE_SEARCH_INTENT, 1.8D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_SEARCH_TERM, 1.5D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_REPRESENTATIVE_QUERY, 1.5D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_CAPABILITY, 1.3D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_DESCRIPTION, 1.1D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_TAG, 1.0D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_AI_SUMMARY, 1.0D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_SKILL_CONTENT, 0.7D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_PROMPT_CONTENT, 0.7D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_MCP_CONTENT, 0.7D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_METADATA_IO, 0.6D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_METADATA_RISK, 0.5D);
        weights.put(ArdIndexConstants.CHUNK_TYPE_NOT_FOR, 0.4D);
        return Collections.unmodifiableMap(weights);
    }
}
