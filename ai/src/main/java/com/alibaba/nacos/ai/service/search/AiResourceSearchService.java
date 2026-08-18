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
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.model.search.AiResourceSearchHit;
import com.alibaba.nacos.ai.model.search.AiResourceSearchResult;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Canonical AI resource discovery application service.
 *
 * <p>The service owns recall, ranking, visibility and current-version checks,
 * canonical field filtering, ordering, and pagination. Protocol adaptors are
 * responsible only for translating their request and response models.</p>
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceSearchService {
    
    private static final int RRF_K = 60;
    
    private static final double RRF_SCORE_SCALE = 100.0D;
    
    private static final double KEYWORD_RRF_WEIGHT = 1.0D;
    
    private static final double VECTOR_RRF_WEIGHT = 0.6D;
    
    private static final int DOCUMENT_LOOKUP_BATCH_SIZE = 500;
    
    private static final int ENTRY_SCAN_BATCH_SIZE = 500;
    
    private static final int DEFAULT_MAX_RECALL_CANDIDATES = 10000;
    
    private static final int DEFAULT_NUMBERED_PAGE_SIZE = 20;
    
    private static final String CURSOR_DOCUMENT_ID = "documentId";
    
    private static final String KEY_RANKING_ENHANCED_ENABLED =
        "nacos.ai.resource.search.ranking.enhanced.enabled";
    
    private static final String KEY_MAX_RECALL_CANDIDATES =
        "nacos.ai.resource.search.max-recall-candidates";
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final Map<String, Double> CHUNK_TYPE_WEIGHTS = chunkTypeWeights();
    
    private final AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    private final AiResourceSearchRepository repository;
    
    private final AiResourceEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    @Autowired
    public AiResourceSearchService(AiResourceSearchTypeHandlerRegistry typeHandlerRegistry,
        AiResourceSearchRepository repository,
        AiResourceEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex) {
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
    }
    
    public AiResourceSearchService(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService, AiResourceSearchRepository repository,
        AiResourceEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex) {
        this(new AiResourceSearchTypeHandlerRegistry(List.of(
            new StoredAiResourceSearchTypeHandler(resourceManager,
                AiResourceIndexContentLoader.NOOP),
            new McpAiResourceSearchTypeHandler(mcpServerOperationService))), repository,
            embeddingService, vectorIndex);
    }
    
    /**
     * Search and relevance-rank current visible resources.
     *
     * @param query canonical discovery query
     * @return paged discovery result
     * @throws NacosException when canonical resource lookup fails
     */
    public Page search(Query query) throws NacosException {
        return page(searchCandidates(query), query);
    }
    
    private List<RankedEntry> searchCandidates(Query query) throws NacosException {
        Map<Long, SearchScore> scores = recall(query);
        List<RankedEntry> candidates = new ArrayList<>();
        for (AiResourceSearchDocument document : findDocumentsByIds(scores.keySet())) {
            if (!matches(query, document) || !validateCurrentResource(document)) {
                continue;
            }
            SearchScore score = scores.get(document.getId());
            double finalScore = score == null ? 0D : score.getScore();
            if (enhancedRankingEnabled()) {
                finalScore += exactMatchBoost(document, query.getText());
            }
            candidates.add(new RankedEntry(document, finalScore));
        }
        candidates.sort(relevanceComparator());
        normalizeScores(candidates);
        return candidates;
    }
    
    /**
     * List current visible resources using canonical filtering and ordering.
     *
     * @param query canonical discovery query
     * @return paged discovery result
     * @throws NacosException when canonical resource lookup fails
     */
    public Page list(Query query) throws NacosException {
        Comparator<RankedEntry> comparator = listComparator(query);
        RankedEntry cursor = listCursor(query);
        PriorityQueue<RankedEntry> selected =
            new PriorityQueue<>(query.getLimit() + 2, comparator.reversed());
        long afterId = 0L;
        while (true) {
            List<AiResourceSearchDocument> batch = repository.scanEnabledEntries(
                query.getNamespaceId(), query.getResourceTypes(), afterId, ENTRY_SCAN_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (AiResourceSearchDocument document : batch) {
                if (matches(query, document) && validateCurrentResource(document)) {
                    RankedEntry candidate = new RankedEntry(document, 0D);
                    if (cursor == null || comparator.compare(candidate, cursor) > 0) {
                        selected.offer(candidate);
                        if (selected.size() > query.getLimit() + 1) {
                            selected.poll();
                        }
                    }
                }
            }
            afterId = lastDocumentId(batch, afterId);
            if (batch.size() < ENTRY_SCAN_BATCH_SIZE) {
                break;
            }
        }
        List<RankedEntry> candidates = new ArrayList<>(selected);
        candidates.sort(listComparator(query));
        return boundedPage(candidates, query);
    }
    
    /**
     * List one numbered page in stable resource-key order.
     *
     * <p>Eligibility checks happen before the total and offset are calculated. The scan retains
     * only the requested page and a total counter.</p>
     *
     * @param query canonical discovery query with numbered-page settings
     * @return numbered page over the complete eligible result set
     * @throws NacosException when canonical resource lookup fails
     */
    public NumberedPage numberedList(Query query) throws NacosException {
        long offset = (long) (query.getPageNumber() - 1) * query.getPageSize();
        long totalCount = 0L;
        List<AiResourceSearchResult> items =
            new ArrayList<>(Math.min(query.getPageSize(), ENTRY_SCAN_BATCH_SIZE));
        String afterResourceType = null;
        String afterResourceName = null;
        long afterId = 0L;
        while (true) {
            List<AiResourceSearchDocument> batch = repository.scanEnabledEntriesByResourceKey(
                query.getNamespaceId(), query.getResourceTypes(), afterResourceType,
                afterResourceName, afterId, ENTRY_SCAN_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (AiResourceSearchDocument document : batch) {
                if (!matches(query, document) || !validateCurrentResource(document)) {
                    continue;
                }
                if (totalCount >= offset && items.size() < query.getPageSize()) {
                    items.add(toResult(new RankedEntry(document, 0D)));
                }
                totalCount++;
            }
            AiResourceSearchDocument last = batch.get(batch.size() - 1);
            validateResourceKeyScanAdvance(last, afterResourceType, afterResourceName, afterId);
            afterResourceType = last.getResourceType();
            afterResourceName = last.getResourceName();
            afterId = last.getId();
            if (batch.size() < ENTRY_SCAN_BATCH_SIZE) {
                break;
            }
        }
        long pageCount = totalCount / query.getPageSize()
            + (totalCount % query.getPageSize() == 0 ? 0 : 1);
        int pagesAvailable = (int) Math.min(Integer.MAX_VALUE, pageCount);
        return new NumberedPage(items, totalCount, query.getPageNumber(), pagesAvailable);
    }
    
    /**
     * Aggregate canonical fields over the complete eligible result set.
     *
     * @param query canonical discovery query
     * @param requests aggregation requests
     * @return aggregation result
     * @throws NacosException when canonical resource lookup fails
     */
    public AggregationResult aggregate(Query query, List<AggregationRequest> requests)
        throws NacosException {
        if (StringUtils.isBlank(query.getText())) {
            return aggregateList(query, requests);
        }
        List<RankedEntry> candidates = searchCandidates(query);
        Map<String, Aggregation> aggregations = new LinkedHashMap<>();
        if (requests != null) {
            for (AggregationRequest request : requests) {
                aggregations.put(request.getName(), aggregateCandidates(candidates, request));
            }
        }
        return new AggregationResult(candidates.size(), aggregations);
    }
    
    private AggregationResult aggregateList(Query query, List<AggregationRequest> requests)
        throws NacosException {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        if (requests != null) {
            for (AggregationRequest request : requests) {
                counts.put(request.getName(), new LinkedHashMap<>());
            }
        }
        int total = 0;
        long afterId = 0L;
        while (true) {
            List<AiResourceSearchDocument> batch = repository.scanEnabledEntries(
                query.getNamespaceId(), query.getResourceTypes(), afterId, ENTRY_SCAN_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (AiResourceSearchDocument document : batch) {
                if (!matches(query, document) || !validateCurrentResource(document)) {
                    continue;
                }
                total++;
                if (requests != null) {
                    for (AggregationRequest request : requests) {
                        recordAggregationValues(counts.get(request.getName()), document,
                            request.getField());
                    }
                }
            }
            afterId = lastDocumentId(batch, afterId);
            if (batch.size() < ENTRY_SCAN_BATCH_SIZE) {
                break;
            }
        }
        Map<String, Aggregation> aggregations = new LinkedHashMap<>();
        if (requests != null) {
            for (AggregationRequest request : requests) {
                aggregations.put(request.getName(),
                    buildAggregation(counts.get(request.getName()), request));
            }
        }
        return new AggregationResult(total, aggregations);
    }
    
    private Aggregation aggregateCandidates(List<RankedEntry> candidates,
        AggregationRequest request) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RankedEntry candidate : candidates) {
            recordAggregationValues(counts, candidate.getDocument(), request.getField());
        }
        return buildAggregation(counts, request);
    }
    
    private void recordAggregationValues(Map<String, Integer> counts,
        AiResourceSearchDocument document, String field) {
        Set<String> values = new LinkedHashSet<>(fieldValues(document, field));
        for (String value : values) {
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }
    }
    
    private Aggregation buildAggregation(Map<String, Integer> counts,
        AggregationRequest request) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() >= request.getMinCount()) {
                sorted.add(count);
            }
        }
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
            .reversed().thenComparing(Map.Entry::getKey));
        List<AggregationBucket> buckets = new ArrayList<>();
        int otherCount = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> count = sorted.get(i);
            if (i < request.getLimit()) {
                buckets.add(new AggregationBucket(count.getKey(), count.getValue()));
            } else {
                otherCount += count.getValue();
            }
        }
        return new Aggregation(buckets, otherCount);
    }
    
    private Map<Long, SearchScore> recall(Query query) throws NacosException {
        int maxCandidates = maxRecallCandidates();
        if (!enhancedRankingEnabled()) {
            return recallWithMaxScore(query, maxCandidates);
        }
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(query.getText());
            List<AiResourceSearchHit> vectorHits = toSearchHits(vectorIndex.search(
                query.getNamespaceId(), embeddingService.model(), vector,
                query.getResourceTypes(), maxCandidates + 1));
            ensureWithinRecallLimit(vectorHits, maxCandidates, "vector");
            recordRrfScores(scores, sortHitsByScore(vectorHits, false), VECTOR_RRF_WEIGHT, false);
        }
        List<AiResourceSearchHit> keywordHits = repository.searchChunks(query.getNamespaceId(),
            query.getText(), query.getResourceTypes(), maxCandidates + 1);
        ensureWithinRecallLimit(keywordHits, maxCandidates, "keyword");
        recordRrfScores(scores, sortHitsByScore(keywordHits, true), KEYWORD_RRF_WEIGHT, true);
        return scores;
    }
    
    private Map<Long, SearchScore> recallWithMaxScore(Query query, int maxCandidates)
        throws NacosException {
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(query.getText());
            List<AiResourceSearchHit> vectorHits = toSearchHits(vectorIndex.search(
                query.getNamespaceId(),
                embeddingService.model(), vector, query.getResourceTypes(),
                maxCandidates + 1));
            ensureWithinRecallLimit(vectorHits, maxCandidates, "vector");
            for (AiResourceSearchHit hit : vectorHits) {
                recordMaxScore(scores, hit);
            }
        }
        List<AiResourceSearchHit> keywordHits = repository.searchChunks(query.getNamespaceId(),
            query.getText(), query.getResourceTypes(), maxCandidates + 1);
        ensureWithinRecallLimit(keywordHits, maxCandidates, "keyword");
        for (AiResourceSearchHit hit : keywordHits) {
            recordMaxScore(scores, hit);
        }
        return scores;
    }
    
    private void ensureWithinRecallLimit(List<AiResourceSearchHit> hits, int limit, String channel)
        throws NacosException {
        if (hits != null && hits.size() > limit) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "AI resource " + channel + " recall exceeded configured candidate limit "
                    + limit);
        }
    }
    
    private List<AiResourceSearchDocument> findDocumentsByIds(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(documentIds);
        List<AiResourceSearchDocument> result = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += DOCUMENT_LOOKUP_BATCH_SIZE) {
            int toIndex = Math.min(offset + DOCUMENT_LOOKUP_BATCH_SIZE, ids.size());
            result.addAll(repository.findEntriesByIds(ids.subList(offset, toIndex)));
        }
        return result;
    }
    
    private List<AiResourceSearchHit> sortHitsByScore(List<AiResourceSearchHit> hits,
        boolean useChunkWeight) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiResourceSearchHit> result = new ArrayList<>(hits);
        result.sort(Comparator.comparing((AiResourceSearchHit hit) -> hitScore(hit, useChunkWeight))
            .reversed().thenComparing(AiResourceSearchHit::getDocumentId,
                Comparator.nullsLast(Long::compareTo))
            .thenComparing(AiResourceSearchHit::getChunkId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }
    
    private double hitScore(AiResourceSearchHit hit, boolean useChunkWeight) {
        if (hit == null) {
            return 0D;
        }
        return useChunkWeight ? hit.getScore() * chunkTypeWeight(hit.getChunkType())
            : hit.getScore();
    }
    
    private List<AiResourceSearchHit> toSearchHits(List<AiResourceVectorHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiResourceSearchHit> result = new ArrayList<>(hits.size());
        for (AiResourceVectorHit hit : hits) {
            if (hit == null) {
                continue;
            }
            AiResourceSearchHit converted = new AiResourceSearchHit();
            converted.setDocumentId(hit.getDocumentId());
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
    
    private void recordRrfScores(Map<Long, SearchScore> scores, List<AiResourceSearchHit> hits,
        double channelWeight, boolean useChunkWeight) {
        Set<Long> seenEntries = new LinkedHashSet<>();
        int rank = 0;
        for (AiResourceSearchHit hit : hits) {
            if (hit == null || hit.getDocumentId() == null
                || !seenEntries.add(hit.getDocumentId())) {
                continue;
            }
            rank++;
            double chunkWeight = useChunkWeight ? chunkTypeWeight(hit.getChunkType()) : 1.0D;
            double score = RRF_SCORE_SCALE * channelWeight * chunkWeight / (RRF_K + rank);
            scores.computeIfAbsent(hit.getDocumentId(), key -> new SearchScore()).add(score);
        }
    }
    
    private void recordMaxScore(Map<Long, SearchScore> scores, AiResourceSearchHit hit) {
        if (hit != null && hit.getDocumentId() != null) {
            scores.computeIfAbsent(hit.getDocumentId(), key -> new SearchScore())
                .max(hit.getScore());
        }
    }
    
    private double exactMatchBoost(AiResourceSearchDocument entry, String query) {
        String normalizedQuery = normalize(query);
        String compactQuery = compact(query);
        double identityBoost = Math.max(identityBoost(entry.getResourceName(), normalizedQuery,
            compactQuery), identityBoost(entry.getDisplayName(), normalizedQuery, compactQuery));
        String resourceKey = entry.getNamespaceId() + ":" + entry.getResourceType() + ":"
            + entry.getResourceName();
        if (StringUtils.isNotBlank(normalizedQuery)
            && normalize(resourceKey).contains(normalizedQuery)) {
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
    
    private boolean containsNormalized(List<String> values, String normalizedQuery) {
        if (values == null || StringUtils.isBlank(normalizedQuery)) {
            return false;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)
                && normalize(value).contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matches(Query query, AiResourceSearchDocument entry) {
        if (!AiResourceSearchConstants.STATUS_ENABLED.equals(entry.getStatus())) {
            return false;
        }
        if (query.getCreatedAfter() != null
            && !isAfter(entry.getGmtCreate(), query.getCreatedAfter())) {
            return false;
        }
        if (query.getUpdatedAfter() != null
            && !isAfter(entry.getGmtModified(), query.getUpdatedAfter())) {
            return false;
        }
        for (Map.Entry<String, List<String>> filter : query.getFilters().entrySet()) {
            if (!matchesAny(filter.getValue(), fieldValues(entry, filter.getKey()),
                "displayName".equals(filter.getKey()))) {
                return false;
            }
        }
        for (Predicate predicate : query.getPredicates()) {
            if (predicate != null
                && !matchesPredicate(predicate, fieldValues(entry, predicate.getField()))) {
                return false;
            }
        }
        return true;
    }
    
    private List<String> fieldValues(AiResourceSearchDocument entry, String field) {
        if (field == null) {
            return Collections.emptyList();
        }
        if ("displayName".equals(field)) {
            return singleton(entry.getDisplayName());
        }
        if ("resourceName".equals(field)) {
            return singleton(entry.getResourceName());
        }
        if ("resourceType".equals(field)) {
            return singleton(entry.getResourceType());
        }
        if ("resourceVersion".equals(field)) {
            return singleton(entry.getResourceVersion());
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
        return Collections.emptyList();
    }
    
    private boolean matchesAny(List<String> expected, List<String> actual, boolean contains) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        for (String expectedValue : expected) {
            if (contains ? containsIgnoreCase(actual, expectedValue)
                : equalsIgnoreCase(actual, expectedValue)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesPredicate(Predicate predicate, List<String> actual) {
        if (predicate.getValues().isEmpty()) {
            return true;
        }
        if (actual.isEmpty()) {
            return false;
        }
        if (PredicateOperator.EXACT_ALL == predicate.getOperator()) {
            for (String expected : predicate.getValues()) {
                if (!matchesExpected(actual, expected, PredicateOperator.EXACT_ANY,
                    predicate.isCaseSensitive())) {
                    return false;
                }
            }
            return true;
        }
        for (String expected : predicate.getValues()) {
            if (matchesExpected(actual, expected, predicate.getOperator(),
                predicate.isCaseSensitive())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesExpected(List<String> actual, String expected,
        PredicateOperator operator, boolean caseSensitive) {
        for (String value : actual) {
            if (value == null || expected == null) {
                continue;
            }
            String comparedValue = caseSensitive ? value : normalize(value);
            String comparedExpected = caseSensitive ? expected : normalize(expected);
            if (PredicateOperator.LITERAL_CONTAINS == operator
                ? comparedValue.contains(comparedExpected)
                : comparedValue.equals(comparedExpected)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean validateCurrentResource(AiResourceSearchDocument entry) throws NacosException {
        return typeHandlerRegistry.isCurrent(entry);
    }
    
    private Comparator<RankedEntry> relevanceComparator() {
        return Comparator.comparingDouble(RankedEntry::getRankScore).reversed()
            .thenComparing(result -> result.getDocument().getGmtModified(),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(result -> result.getDocument().getId(),
                Comparator.nullsLast(Long::compareTo));
    }
    
    private Comparator<RankedEntry> listComparator(Query query) {
        Comparator<RankedEntry> comparator;
        if (Sort.DISPLAY_NAME == query.getSort()) {
            comparator = Comparator.comparing(result -> result.getDocument().getDisplayName(),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else if (Sort.RESOURCE_KEY == query.getSort()) {
            comparator = Comparator
                .comparing((RankedEntry result) -> result.getDocument().getResourceType(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(result -> result.getDocument().getResourceName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else {
            comparator = Comparator.comparing(result -> result.getDocument().getGmtModified(),
                Comparator.nullsLast(Timestamp::compareTo));
        }
        if (query.isDescending()) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(result -> result.getDocument().getId(),
            Comparator.nullsLast(Long::compareTo));
    }
    
    private void normalizeScores(List<RankedEntry> candidates) {
        double maxScore = 0D;
        for (RankedEntry candidate : candidates) {
            maxScore = Math.max(maxScore, candidate.getRankScore());
        }
        for (RankedEntry candidate : candidates) {
            candidate.setScore(normalizeScore(candidate.getRankScore(), maxScore));
        }
    }
    
    private int normalizeScore(double score, double maxScore) {
        if (score <= 0D || maxScore <= 0D) {
            return 0;
        }
        return (int) Math.min(100L, Math.round(score * 100D / maxScore));
    }
    
    private Page page(List<RankedEntry> candidates, Query query) throws NacosException {
        int fromIndex = cursorIndex(candidates, query.getCursor());
        int toIndex = Math.min(fromIndex + query.getLimit(), candidates.size());
        List<AiResourceSearchResult> items = new ArrayList<>();
        for (RankedEntry candidate : candidates.subList(fromIndex, toIndex)) {
            items.add(toResult(candidate));
        }
        String nextCursor = toIndex < candidates.size()
            ? encodeCursor(candidates.get(toIndex - 1).getDocument().getId()) : null;
        return new Page(items, nextCursor);
    }
    
    private Page boundedPage(List<RankedEntry> candidates, Query query) {
        int toIndex = Math.min(query.getLimit(), candidates.size());
        List<AiResourceSearchResult> items = new ArrayList<>();
        for (RankedEntry candidate : candidates.subList(0, toIndex)) {
            items.add(toResult(candidate));
        }
        String nextCursor = toIndex < candidates.size()
            ? encodeCursor(candidates.get(toIndex - 1).getDocument().getId()) : null;
        return new Page(items, nextCursor);
    }
    
    private RankedEntry listCursor(Query query) throws NacosException {
        if (StringUtils.isBlank(query.getCursor())) {
            return null;
        }
        Long documentId = decodeCursor(query.getCursor());
        List<AiResourceSearchDocument> documents =
            repository.findEntriesByIds(Collections.singletonList(documentId));
        if (documents == null || documents.size() != 1) {
            throw invalidCursor();
        }
        AiResourceSearchDocument document = documents.get(0);
        if (!query.getNamespaceId().equals(document.getNamespaceId())
            || !query.getResourceTypes().isEmpty()
                && !query.getResourceTypes().contains(document.getResourceType())
            || !matches(query, document) || !validateCurrentResource(document)) {
            throw invalidCursor();
        }
        return new RankedEntry(document, 0D);
    }
    
    private long lastDocumentId(List<AiResourceSearchDocument> batch, long previousId) {
        AiResourceSearchDocument last = batch.get(batch.size() - 1);
        if (last.getId() == null || last.getId() <= previousId) {
            throw new IllegalStateException("AI resource index scan did not advance");
        }
        return last.getId();
    }
    
    private void validateResourceKeyScanAdvance(AiResourceSearchDocument last,
        String previousResourceType, String previousResourceName, long previousId) {
        if (last.getId() == null || last.getResourceType() == null
            || last.getResourceName() == null) {
            throw new IllegalStateException("AI resource key scan returned an incomplete anchor");
        }
        if (previousResourceType == null) {
            return;
        }
        int typeComparison = last.getResourceType().compareTo(previousResourceType);
        int nameComparison = typeComparison == 0
            ? last.getResourceName().compareTo(previousResourceName) : 0;
        if (typeComparison < 0 || typeComparison == 0 && nameComparison < 0
            || typeComparison == 0 && nameComparison == 0 && last.getId() <= previousId) {
            throw new IllegalStateException("AI resource key scan did not advance");
        }
    }
    
    private AiResourceSearchResult toResult(RankedEntry candidate) {
        AiResourceSearchDocument document = candidate.getDocument();
        AiResourceSearchResult result = new AiResourceSearchResult();
        result.setNamespaceId(document.getNamespaceId());
        result.setResourceType(document.getResourceType());
        result.setResourceName(document.getResourceName());
        result.setResourceVersion(document.getResourceVersion());
        result.setDisplayName(document.getDisplayName());
        result.setDescription(document.getDescription());
        result.setTags(parseStringList(document.getTags()));
        result.setCapabilities(parseStringList(document.getCapabilities()));
        result.setRepresentativeQueries(parseStringList(document.getRepresentativeQueries()));
        result.setMetadata(parseMap(document.getMetadata()));
        result.setGmtCreate(document.getGmtCreate());
        result.setGmtModified(document.getGmtModified());
        result.setScore(candidate.getScore());
        return result;
    }
    
    private int cursorIndex(List<RankedEntry> candidates, String cursor)
        throws NacosException {
        if (StringUtils.isBlank(cursor)) {
            return 0;
        }
        Long documentId = decodeCursor(cursor);
        for (int i = 0; i < candidates.size(); i++) {
            if (documentId.equals(candidates.get(i).getDocument().getId())) {
                return i + 1;
            }
        }
        throw invalidCursor();
    }
    
    private String encodeCursor(Long documentId) {
        Map<String, Object> cursor = new LinkedHashMap<>();
        cursor.put(CURSOR_DOCUMENT_ID, documentId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            JacksonUtils.toJson(cursor).getBytes(StandardCharsets.UTF_8));
    }
    
    private Long decodeCursor(String cursor) throws NacosException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            Map<String, Object> values = JacksonUtils.toObj(
                new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
            Object documentId = values == null ? null : values.get(CURSOR_DOCUMENT_ID);
            if (documentId instanceof Number) {
                return ((Number) documentId).longValue();
            }
            if (documentId instanceof String && StringUtils.isNotBlank((String) documentId)) {
                return Long.parseLong((String) documentId);
            }
        } catch (Exception ignored) {
            throw invalidCursor();
        }
        throw invalidCursor();
    }
    
    private NacosException invalidCursor() {
        return new NacosException(NacosException.INVALID_PARAM, "Invalid discovery cursor");
    }
    
    private boolean enhancedRankingEnabled() {
        String value = System.getProperty(KEY_RANKING_ENHANCED_ENABLED);
        if (StringUtils.isBlank(value)) {
            try {
                value = EnvUtil.getProperty(KEY_RANKING_ENHANCED_ENABLED, "true");
            } catch (Exception ignored) {
                value = "true";
            }
        }
        return Boolean.parseBoolean(value);
    }
    
    private int maxRecallCandidates() {
        String value = System.getProperty(KEY_MAX_RECALL_CANDIDATES);
        if (StringUtils.isBlank(value)) {
            try {
                value = EnvUtil.getProperty(KEY_MAX_RECALL_CANDIDATES,
                    String.valueOf(DEFAULT_MAX_RECALL_CANDIDATES));
            } catch (Exception ignored) {
                value = String.valueOf(DEFAULT_MAX_RECALL_CANDIDATES);
            }
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 && parsed < Integer.MAX_VALUE
                ? parsed : DEFAULT_MAX_RECALL_CANDIDATES;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_RECALL_CANDIDATES;
        }
    }
    
    private double chunkTypeWeight(String chunkType) {
        Double weight = CHUNK_TYPE_WEIGHTS.get(chunkType);
        return weight == null ? 1.0D : weight;
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
    
    private List<String> singleton(String value) {
        return StringUtils.isBlank(value) ? Collections.emptyList()
            : Collections.singletonList(value);
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
    
    private boolean isAfter(Timestamp value, Instant threshold) {
        return value != null && threshold != null && value.toInstant().isAfter(threshold);
    }
    
    private static Map<String, Double> chunkTypeWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT, 1.8D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM, 1.5D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_REPRESENTATIVE_QUERY, 1.5D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_CAPABILITY, 1.3D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_DESCRIPTION, 1.1D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_TAG, 1.0D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_AI_SUMMARY, 1.0D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_SKILL_CONTENT, 0.7D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_PROMPT_CONTENT, 0.7D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_MCP_CONTENT, 0.7D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_METADATA_IO, 0.6D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_METADATA_RISK, 0.5D);
        weights.put(AiResourceSearchConstants.CHUNK_TYPE_NOT_FOR, 0.4D);
        return Collections.unmodifiableMap(weights);
    }
    
    /**
     * Canonical resource discovery query.
     */
    public static class Query {
        
        private String namespaceId;
        
        private String text;
        
        private List<String> resourceTypes = Collections.emptyList();
        
        private Map<String, List<String>> filters = Collections.emptyMap();
        
        private List<Predicate> predicates = Collections.emptyList();
        
        private String cursor;
        
        private int limit;
        
        private Sort sort = Sort.UPDATED_AT;
        
        private boolean descending = true;
        
        private Instant createdAfter;
        
        private Instant updatedAfter;
        
        private int pageNumber = 1;
        
        private int pageSize = DEFAULT_NUMBERED_PAGE_SIZE;
        
        public String getNamespaceId() {
            return namespaceId;
        }
        
        public void setNamespaceId(String namespaceId) {
            this.namespaceId = namespaceId;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public List<String> getResourceTypes() {
            return resourceTypes;
        }
        
        public void setResourceTypes(List<String> resourceTypes) {
            this.resourceTypes = resourceTypes == null ? Collections.emptyList()
                : resourceTypes;
        }
        
        public Map<String, List<String>> getFilters() {
            return filters;
        }
        
        public void setFilters(Map<String, List<String>> filters) {
            this.filters = filters == null ? Collections.emptyMap() : filters;
        }
        
        public List<Predicate> getPredicates() {
            return predicates;
        }
        
        public void setPredicates(List<Predicate> predicates) {
            this.predicates = predicates == null ? Collections.emptyList() : predicates;
        }
        
        public String getCursor() {
            return cursor;
        }
        
        public void setCursor(String cursor) {
            this.cursor = cursor;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public void setLimit(int limit) {
            this.limit = Math.max(1, limit);
        }
        
        public Sort getSort() {
            return sort;
        }
        
        public void setSort(Sort sort) {
            this.sort = sort == null ? Sort.UPDATED_AT : sort;
        }
        
        public boolean isDescending() {
            return descending;
        }
        
        public void setDescending(boolean descending) {
            this.descending = descending;
        }
        
        public Instant getCreatedAfter() {
            return createdAfter;
        }
        
        public void setCreatedAfter(Instant createdAfter) {
            this.createdAfter = createdAfter;
        }
        
        public Instant getUpdatedAfter() {
            return updatedAfter;
        }
        
        public void setUpdatedAfter(Instant updatedAfter) {
            this.updatedAfter = updatedAfter;
        }
        
        public int getPageNumber() {
            return pageNumber;
        }
        
        public void setPageNumber(int pageNumber) {
            this.pageNumber = Math.max(1, pageNumber);
        }
        
        public int getPageSize() {
            return pageSize;
        }
        
        public void setPageSize(int pageSize) {
            this.pageSize = Math.max(1, pageSize);
        }
    }
    
    /**
     * Protocol-neutral structured predicate.
     */
    public static class Predicate {
        
        private String field;
        
        private PredicateOperator operator = PredicateOperator.EXACT_ANY;
        
        private List<String> values = Collections.emptyList();
        
        private boolean caseSensitive;
        
        public Predicate() {
        }
        
        public Predicate(String field, PredicateOperator operator, List<String> values,
            boolean caseSensitive) {
            this.field = field;
            setOperator(operator);
            setValues(values);
            this.caseSensitive = caseSensitive;
        }
        
        public String getField() {
            return field;
        }
        
        public void setField(String field) {
            this.field = field;
        }
        
        public PredicateOperator getOperator() {
            return operator;
        }
        
        public void setOperator(PredicateOperator operator) {
            this.operator = operator == null ? PredicateOperator.EXACT_ANY : operator;
        }
        
        public List<String> getValues() {
            return values;
        }
        
        public void setValues(List<String> values) {
            this.values = values == null ? Collections.emptyList() : values;
        }
        
        public boolean isCaseSensitive() {
            return caseSensitive;
        }
        
        public void setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }
    }
    
    /**
     * Supported structured predicate operators.
     */
    public enum PredicateOperator {
        EXACT_ANY,
        EXACT_ALL,
        LITERAL_CONTAINS
    }
    
    /**
     * Canonical discovery ordering.
     */
    public enum Sort {
        UPDATED_AT,
        DISPLAY_NAME,
        RESOURCE_KEY
    }
    
    /**
     * Canonical discovery page.
     */
    public static class Page {
        
        private final List<AiResourceSearchResult> items;
        
        private final String nextCursor;
        
        public Page(List<AiResourceSearchResult> items, String nextCursor) {
            this.items = items;
            this.nextCursor = nextCursor;
        }
        
        public List<AiResourceSearchResult> getItems() {
            return items;
        }
        
        public String getNextCursor() {
            return nextCursor;
        }
    }
    
    /**
     * Canonical numbered discovery page.
     */
    public static class NumberedPage {
        
        private final List<AiResourceSearchResult> items;
        
        private final long totalCount;
        
        private final int pageNumber;
        
        private final int pagesAvailable;
        
        public NumberedPage(List<AiResourceSearchResult> items, long totalCount, int pageNumber,
            int pagesAvailable) {
            this.items = items;
            this.totalCount = totalCount;
            this.pageNumber = pageNumber;
            this.pagesAvailable = pagesAvailable;
        }
        
        public List<AiResourceSearchResult> getItems() {
            return items;
        }
        
        public long getTotalCount() {
            return totalCount;
        }
        
        public int getPageNumber() {
            return pageNumber;
        }
        
        public int getPagesAvailable() {
            return pagesAvailable;
        }
    }
    
    /**
     * Canonical aggregation request.
     */
    public static class AggregationRequest {
        
        private final String name;
        
        private final String field;
        
        private final int limit;
        
        private final int minCount;
        
        public AggregationRequest(String field, int limit, int minCount) {
            this(field, field, limit, minCount);
        }
        
        public AggregationRequest(String name, String field, int limit, int minCount) {
            this.name = name;
            this.field = field;
            this.limit = Math.max(1, limit);
            this.minCount = Math.max(1, minCount);
        }
        
        public String getName() {
            return name;
        }
        
        public String getField() {
            return field;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public int getMinCount() {
            return minCount;
        }
    }
    
    /**
     * One canonical aggregation bucket.
     */
    public static class AggregationBucket {
        
        private final String value;
        
        private final int count;
        
        public AggregationBucket(String value, int count) {
            this.value = value;
            this.count = count;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    /**
     * Canonical aggregation for one field.
     */
    public static class Aggregation {
        
        private final List<AggregationBucket> buckets;
        
        private final int otherCount;
        
        public Aggregation(List<AggregationBucket> buckets, int otherCount) {
            this.buckets = buckets;
            this.otherCount = otherCount;
        }
        
        public List<AggregationBucket> getBuckets() {
            return buckets;
        }
        
        public int getOtherCount() {
            return otherCount;
        }
    }
    
    /**
     * Canonical aggregation result.
     */
    public static class AggregationResult {
        
        private final int totalMatched;
        
        private final Map<String, Aggregation> aggregations;
        
        public AggregationResult(int totalMatched, Map<String, Aggregation> aggregations) {
            this.totalMatched = totalMatched;
            this.aggregations = aggregations;
        }
        
        public int getTotalMatched() {
            return totalMatched;
        }
        
        public Map<String, Aggregation> getAggregations() {
            return aggregations;
        }
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
    
    private static class RankedEntry {
        
        private final AiResourceSearchDocument document;
        
        private final double rankScore;
        
        private Integer score;
        
        RankedEntry(AiResourceSearchDocument document, double rankScore) {
            this.document = document;
            this.rankScore = rankScore;
        }
        
        AiResourceSearchDocument getDocument() {
            return document;
        }
        
        double getRankScore() {
            return rankScore;
        }
        
        Integer getScore() {
            return score;
        }
        
        void setScore(Integer score) {
            this.score = score;
        }
    }
}
