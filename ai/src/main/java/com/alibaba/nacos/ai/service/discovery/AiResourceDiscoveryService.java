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

package com.alibaba.nacos.ai.service.discovery;

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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
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
@ConditionalOnArdEnabled
public class AiResourceDiscoveryService {
    
    private static final int RRF_K = 60;
    
    private static final double RRF_SCORE_SCALE = 100.0D;
    
    private static final double KEYWORD_RRF_WEIGHT = 1.0D;
    
    private static final double VECTOR_RRF_WEIGHT = 0.6D;
    
    private static final int ENTRY_LOOKUP_BATCH_SIZE = 500;
    
    private static final int ALL_RECALL_CANDIDATES = Integer.MAX_VALUE;
    
    private static final String CURSOR_ENTRY_ID = "entryId";
    
    private static final String KEY_RANKING_ENHANCED_ENABLED =
        "nacos.ai.ard.search.ranking.enhanced.enabled";
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final Map<String, Double> CHUNK_TYPE_WEIGHTS = chunkTypeWeights();
    
    private final AiResourceManager resourceManager;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final ArdIndexRepository repository;
    
    private final ArdEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    public AiResourceDiscoveryService(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService, ArdIndexRepository repository,
        ArdEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex) {
        this.resourceManager = resourceManager;
        this.mcpServerOperationService = mcpServerOperationService;
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
    }
    
    /**
     * Search and relevance-rank current visible resources.
     *
     * @param query canonical discovery query
     * @return paged discovery result
     * @throws NacosException when canonical resource lookup fails
     */
    public Page search(Query query) throws NacosException {
        Map<Long, SearchScore> scores = recall(query);
        List<RankedEntry> candidates = new ArrayList<>();
        for (ArdEntry entry : findEntriesByIds(scores.keySet())) {
            if (!matches(query, entry) || !validateCurrentResource(entry)) {
                continue;
            }
            SearchScore score = scores.get(entry.getId());
            double finalScore = score == null ? 0D : score.getScore();
            if (enhancedRankingEnabled()) {
                finalScore += exactMatchBoost(entry, query.getText());
            }
            candidates.add(new RankedEntry(entry, finalScore));
        }
        candidates.sort(relevanceComparator());
        normalizeScores(candidates);
        return page(candidates, query);
    }
    
    /**
     * List current visible resources using canonical filtering and ordering.
     *
     * @param query canonical discovery query
     * @return paged discovery result
     * @throws NacosException when canonical resource lookup fails
     */
    public Page list(Query query) throws NacosException {
        List<RankedEntry> candidates = new ArrayList<>();
        for (ArdEntry entry : repository.listEnabledEntries(query.getNamespaceId(),
            query.getResourceTypes(), Integer.MAX_VALUE)) {
            if (matches(query, entry) && validateCurrentResource(entry)) {
                candidates.add(new RankedEntry(entry, 0D));
            }
        }
        candidates.sort(listComparator(query));
        return page(candidates, query);
    }
    
    private Map<Long, SearchScore> recall(Query query) {
        if (!enhancedRankingEnabled()) {
            return recallWithMaxScore(query);
        }
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(query.getText());
            recordRrfScores(scores, sortHitsByScore(toSearchHits(vectorIndex.search(
                query.getNamespaceId(), embeddingService.model(), vector,
                query.getResourceTypes(), ALL_RECALL_CANDIDATES)), false), VECTOR_RRF_WEIGHT,
                false);
        }
        recordRrfScores(scores, sortHitsByScore(repository.searchChunks(query.getNamespaceId(),
            query.getText(), query.getResourceTypes(), ALL_RECALL_CANDIDATES), true),
            KEYWORD_RRF_WEIGHT, true);
        return scores;
    }
    
    private Map<Long, SearchScore> recallWithMaxScore(Query query) {
        Map<Long, SearchScore> scores = new LinkedHashMap<>();
        if (vectorIndex.available()) {
            double[] vector = embeddingService.embed(query.getText());
            for (ArdSearchHit hit : toSearchHits(vectorIndex.search(query.getNamespaceId(),
                embeddingService.model(), vector, query.getResourceTypes(),
                ALL_RECALL_CANDIDATES))) {
                recordMaxScore(scores, hit);
            }
        }
        for (ArdSearchHit hit : repository.searchChunks(query.getNamespaceId(), query.getText(),
            query.getResourceTypes(), ALL_RECALL_CANDIDATES)) {
            recordMaxScore(scores, hit);
        }
        return scores;
    }
    
    private List<ArdEntry> findEntriesByIds(Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(entryIds);
        List<ArdEntry> result = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += ENTRY_LOOKUP_BATCH_SIZE) {
            int toIndex = Math.min(offset + ENTRY_LOOKUP_BATCH_SIZE, ids.size());
            result.addAll(repository.findEntriesByIds(ids.subList(offset, toIndex)));
        }
        return result;
    }
    
    private List<ArdSearchHit> sortHitsByScore(List<ArdSearchHit> hits,
        boolean useChunkWeight) {
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
        return useChunkWeight ? hit.getScore() * chunkTypeWeight(hit.getChunkType())
            : hit.getScore();
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
        if (hit != null && hit.getEntryId() != null) {
            scores.computeIfAbsent(hit.getEntryId(), key -> new SearchScore())
                .max(hit.getScore());
        }
    }
    
    private double exactMatchBoost(ArdEntry entry, String query) {
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
    
    private boolean matches(Query query, ArdEntry entry) {
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
        return true;
    }
    
    private List<String> fieldValues(ArdEntry entry, String field) {
        if ("displayName".equals(field)) {
            return singleton(entry.getDisplayName());
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
    
    private boolean validateCurrentResource(ArdEntry entry) throws NacosException {
        return AiResourceConstants.RESOURCE_TYPE_MCP.equals(entry.getResourceType())
            ? validateMcp(entry) : validateAiResource(entry);
    }
    
    private boolean validateAiResource(ArdEntry entry) throws NacosException {
        AiResource meta = resourceManager.findMeta(entry.getNamespaceId(),
            entry.getResourceName(), entry.getResourceType());
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
    
    private boolean validateMcp(ArdEntry entry) {
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
    
    private Comparator<RankedEntry> relevanceComparator() {
        return Comparator.comparingDouble(RankedEntry::getRankScore).reversed()
            .thenComparing(result -> result.getEntry().getGmtModified(),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(result -> result.getEntry().getId(),
                Comparator.nullsLast(Long::compareTo));
    }
    
    private Comparator<RankedEntry> listComparator(Query query) {
        Comparator<RankedEntry> comparator;
        if (Sort.DISPLAY_NAME == query.getSort()) {
            comparator = Comparator.comparing(result -> result.getEntry().getDisplayName(),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else if (Sort.RESOURCE_KEY == query.getSort()) {
            comparator = Comparator
                .comparing((RankedEntry result) -> result.getEntry().getResourceType(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(result -> result.getEntry().getResourceName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else {
            comparator = Comparator.comparing(result -> result.getEntry().getGmtModified(),
                Comparator.nullsLast(Timestamp::compareTo));
        }
        if (query.isDescending()) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(result -> result.getEntry().getId(),
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
        List<Item> items = new ArrayList<>();
        for (RankedEntry candidate : candidates.subList(fromIndex, toIndex)) {
            items.add(new Item(candidate.getEntry(), candidate.getScore()));
        }
        String nextCursor = toIndex < candidates.size()
            ? encodeCursor(candidates.get(toIndex - 1).getEntry().getId()) : null;
        return new Page(items, nextCursor);
    }
    
    private int cursorIndex(List<RankedEntry> candidates, String cursor)
        throws NacosException {
        if (StringUtils.isBlank(cursor)) {
            return 0;
        }
        Long entryId = decodeCursor(cursor);
        for (int i = 0; i < candidates.size(); i++) {
            if (entryId.equals(candidates.get(i).getEntry().getId())) {
                return i + 1;
            }
        }
        throw invalidCursor();
    }
    
    private String encodeCursor(Long entryId) {
        Map<String, Object> cursor = new LinkedHashMap<>();
        cursor.put(CURSOR_ENTRY_ID, entryId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            JacksonUtils.toJson(cursor).getBytes(StandardCharsets.UTF_8));
    }
    
    private Long decodeCursor(String cursor) throws NacosException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            Map<String, Object> values = JacksonUtils.toObj(
                new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
            Object entryId = values == null ? null : values.get(CURSOR_ENTRY_ID);
            if (entryId instanceof Number) {
                return ((Number) entryId).longValue();
            }
            if (entryId instanceof String && StringUtils.isNotBlank((String) entryId)) {
                return Long.parseLong((String) entryId);
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
    
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
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
    
    /**
     * Canonical resource discovery query.
     */
    public static class Query {
        
        private String namespaceId;
        
        private String text;
        
        private List<String> resourceTypes = Collections.emptyList();
        
        private Map<String, List<String>> filters = Collections.emptyMap();
        
        private String cursor;
        
        private int limit;
        
        private Sort sort = Sort.UPDATED_AT;
        
        private boolean descending = true;
        
        private Instant createdAfter;
        
        private Instant updatedAfter;
        
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
     * One canonical discovery result.
     */
    public static class Item {
        
        private final ArdEntry entry;
        
        private final Integer score;
        
        public Item(ArdEntry entry, Integer score) {
            this.entry = entry;
            this.score = score;
        }
        
        public ArdEntry getEntry() {
            return entry;
        }
        
        public Integer getScore() {
            return score;
        }
    }
    
    /**
     * Canonical discovery page.
     */
    public static class Page {
        
        private final List<Item> items;
        
        private final String nextCursor;
        
        public Page(List<Item> items, String nextCursor) {
            this.items = items;
            this.nextCursor = nextCursor;
        }
        
        public List<Item> getItems() {
            return items;
        }
        
        public String getNextCursor() {
            return nextCursor;
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
        
        private final ArdEntry entry;
        
        private final double rankScore;
        
        private Integer score;
        
        RankedEntry(ArdEntry entry, double rankScore) {
            this.entry = entry;
            this.rankScore = rankScore;
        }
        
        ArdEntry getEntry() {
            return entry;
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
