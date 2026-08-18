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
import com.alibaba.nacos.ai.model.search.AiResourceSearchHit;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.AggregationRequest;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.AggregationResult;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.NumberedPage;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Page;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Predicate;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.PredicateOperator;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Query;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiResourceSearchService}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiResourceSearchServiceTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private AiResourceSearchRepository repository;
    
    @Mock
    private AiResourceEmbeddingService embeddingService;
    
    @Mock
    private AiResourceVectorIndex vectorIndex;
    
    @Test
    void searchShouldFilterVisibilityAndCurrentVersionBeforePagination() throws Exception {
        final AiResourceSearchService service = new AiResourceSearchService(
            resourceManager, mcpServerOperationService, repository, embeddingService,
            vectorIndex);
        List<AiResourceSearchHit> hits = new ArrayList<>();
        List<AiResourceSearchDocument> staleEntries = new ArrayList<>();
        for (long id = 1; id <= 500; id++) {
            hits.add(hit(id));
            staleEntries.add(entry(id, "stale-" + id));
        }
        AiResourceSearchDocument visibleEntry = entry(501L, "visible");
        hits.add(hit(501L));
        
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks("public", "api", List.of("skill"),
            10001)).thenReturn(hits);
        when(repository.findEntriesByIds(any())).thenReturn(staleEntries,
            List.of(visibleEntry));
        when(resourceManager.findMeta(eq("public"), any(), eq("skill"))).thenReturn(null);
        when(resourceManager.findMeta("public", "visible", "skill"))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "visible", "skill", "1.0.0"))
            .thenReturn(onlineVersion());
        
        Query query = new Query();
        query.setNamespaceId("public");
        query.setText("api");
        query.setResourceTypes(List.of("skill"));
        query.setLimit(1);
        
        Page page = service.search(query);
        
        assertEquals(1, page.getItems().size());
        assertEquals("visible", page.getItems().get(0).getResourceName());
        verify(repository, times(2)).findEntriesByIds(any());
    }
    
    @Test
    void searchShouldFailInsteadOfSilentlyTruncatingRecallCandidates() {
        String key = "nacos.ai.resource.search.max-recall-candidates";
        System.setProperty(key, "2");
        try {
            AiResourceSearchService service = new AiResourceSearchService(resourceManager,
                mcpServerOperationService, repository, embeddingService, vectorIndex);
            when(vectorIndex.available()).thenReturn(false);
            when(repository.searchChunks("public", "api", List.of("skill"), 3))
                .thenReturn(List.of(hit(1L), hit(2L), hit(3L)));
            Query query = new Query();
            query.setNamespaceId("public");
            query.setText("api");
            query.setResourceTypes(List.of("skill"));
            query.setLimit(1);
            
            assertThrows(NacosException.class, () -> service.search(query));
        } finally {
            System.clearProperty(key);
        }
    }
    
    @Test
    void listShouldContinueAfterOpaqueResourceCursor() throws Exception {
        final AiResourceSearchService service = new AiResourceSearchService(
            resourceManager, mcpServerOperationService, repository, embeddingService,
            vectorIndex);
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(List.of(entry(1L, "one"), entry(2L, "two"), entry(3L, "three")));
        when(repository.findEntriesByIds(List.of(2L)))
            .thenReturn(List.of(entry(2L, "two")));
        when(resourceManager.findMeta(eq("public"), any(), eq("skill")))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(), eq("skill"), eq("1.0.0")))
            .thenReturn(onlineVersion());
        
        Query firstQuery = new Query();
        firstQuery.setNamespaceId("public");
        firstQuery.setResourceTypes(List.of("skill"));
        firstQuery.setLimit(2);
        
        Page firstPage = service.list(firstQuery);
        
        assertEquals(List.of("three", "two"), List.of(
            firstPage.getItems().get(0).getResourceName(),
            firstPage.getItems().get(1).getResourceName()));
        assertNotNull(firstPage.getNextCursor());
        
        Query secondQuery = new Query();
        secondQuery.setNamespaceId("public");
        secondQuery.setResourceTypes(List.of("skill"));
        secondQuery.setLimit(2);
        secondQuery.setCursor(firstPage.getNextCursor());
        
        Page secondPage = service.list(secondQuery);
        
        assertEquals(1, secondPage.getItems().size());
        assertEquals("one", secondPage.getItems().get(0).getResourceName());
    }
    
    @Test
    void aggregateShouldUseCompleteEligibleResultSet() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        List<AiResourceSearchDocument> documents = new ArrayList<>();
        for (long id = 1; id <= 1001; id++) {
            AiResourceSearchDocument document = entry(id, "skill-" + id);
            document.setTags(JacksonUtils.toJson(List.of("shared")));
            documents.add(document);
        }
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(documents.subList(0, 500));
        when(repository.scanEnabledEntries("public", List.of("skill"), 500L, 500))
            .thenReturn(documents.subList(500, 1000));
        when(repository.scanEnabledEntries("public", List.of("skill"), 1000L, 500))
            .thenReturn(documents.subList(1000, 1001));
        when(resourceManager.findMeta(eq("public"), any(), eq("skill")))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(), eq("skill"), eq("1.0.0")))
            .thenReturn(onlineVersion());
        
        Query query = new Query();
        query.setNamespaceId("public");
        query.setResourceTypes(List.of("skill"));
        
        AggregationResult result = service.aggregate(query,
            List.of(new AggregationRequest("tags", 10, 1)));
        
        assertEquals(1001, result.getTotalMatched());
        assertEquals(1001, result.getAggregations().get("tags").getBuckets().get(0).getCount());
    }
    
    @Test
    void listShouldApplyTypedPredicatesWithLiteralSpecialCharacters() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        AiResourceSearchDocument matching = entry(1L, "Agent_100%");
        matching.setDisplayName("Research%_\\Assistant");
        matching.setTags(JacksonUtils.toJson(List.of("Research", "CITATIONS")));
        matching.setMetadata(JacksonUtils.toJson(Map.of("protocols", List.of("A2A", "MCP"))));
        AiResourceSearchDocument wildcardLookalike = entry(2L, "Agent_100X");
        wildcardLookalike.setDisplayName("ResearchABAssistant");
        wildcardLookalike.setTags(JacksonUtils.toJson(List.of("Research", "CITATIONS")));
        wildcardLookalike.setMetadata(JacksonUtils.toJson(Map.of("protocols", List.of("A2A"))));
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(List.of(matching, wildcardLookalike));
        mockCurrentResources();
        Query query = listQuery();
        query.setPredicates(Arrays.asList(null,
            new Predicate("tags", PredicateOperator.EXACT_ALL,
                List.of("research", "citations"), false),
            new Predicate("displayName", PredicateOperator.LITERAL_CONTAINS,
                List.of("%_\\"), true),
            new Predicate("metadata.protocols", PredicateOperator.EXACT_ANY,
                List.of("a2a"), false),
            new Predicate("resourceName", PredicateOperator.EXACT_ANY,
                List.of("Agent_100%"), true),
            new Predicate("unused", PredicateOperator.EXACT_ALL, List.of(), false)));
        
        Page page = service.list(query);
        
        assertEquals(1, page.getItems().size());
        assertEquals("Agent_100%", page.getItems().get(0).getResourceName());
    }
    
    @Test
    void listShouldRespectPredicateCaseSensitivityAndLegacyFilterSemantics() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        AiResourceSearchDocument document = entry(1L, "AgentOne");
        document.setDisplayName("Research Assistant");
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(List.of(document));
        mockCurrentResources();
        Query query = listQuery();
        Predicate predicate = new Predicate("resourceName", PredicateOperator.EXACT_ANY,
            List.of("agentone"), true);
        query.setPredicates(List.of(predicate));
        
        assertEquals(0, service.list(query).getItems().size());
        predicate.setCaseSensitive(false);
        query.setFilters(Map.of("displayName", List.of("assistant")));
        assertEquals(1, service.list(query).getItems().size());
    }
    
    @Test
    void predicatesShouldRejectMissingFieldsMissingValuesAndNullTokens() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        AiResourceSearchDocument document = entry(1L, "AgentOne");
        document.setTags(JacksonUtils.toJson(Arrays.asList("value", null)));
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(List.of(document));
        Query query = listQuery();
        query.setPredicates(List.of(new Predicate("tags", PredicateOperator.EXACT_ALL,
            List.of("value", "missing"), false)));
        assertEquals(0, service.list(query).getItems().size());
        query.setPredicates(List.of(new Predicate("unknown", PredicateOperator.EXACT_ANY,
            List.of("value"), false)));
        assertEquals(0, service.list(query).getItems().size());
        query.setPredicates(List.of(new Predicate("tags", PredicateOperator.EXACT_ANY,
            Collections.singletonList(null), false)));
        assertEquals(0, service.list(query).getItems().size());
        query.setPredicates(List.of(new Predicate(null, PredicateOperator.EXACT_ANY,
            List.of("value"), false)));
        assertEquals(0, service.list(query).getItems().size());
    }
    
    @Test
    void numberedListShouldFilterBeforeTotalAndOffset() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        AiResourceSearchDocument alpha = entry(1L, "alpha");
        AiResourceSearchDocument stale = entry(2L, "bravo");
        AiResourceSearchDocument charlie = entry(3L, "charlie");
        AiResourceSearchDocument disabled = entry(4L, "delta");
        disabled.setStatus("disabled");
        AiResourceSearchDocument echo = entry(5L, "echo");
        AiResourceSearchDocument foxtrot = entry(6L, "foxtrot");
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(List.of(alpha, stale, charlie, disabled, echo, foxtrot));
        when(resourceManager.findMeta(eq("public"), any(), eq("skill")))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findMeta("public", "bravo", "skill")).thenReturn(null);
        when(resourceManager.findVersion(eq("public"), any(), eq("skill"), eq("1.0.0")))
            .thenReturn(onlineVersion());
        Query query = listQuery();
        query.setPageNumber(2);
        query.setPageSize(2);
        
        NumberedPage secondPage = service.numberedList(query);
        
        assertEquals(4L, secondPage.getTotalCount());
        assertEquals(2, secondPage.getPageNumber());
        assertEquals(2, secondPage.getPagesAvailable());
        assertEquals(List.of("echo", "foxtrot"), List.of(
            secondPage.getItems().get(0).getResourceName(),
            secondPage.getItems().get(1).getResourceName()));
        query.setPageNumber(3);
        NumberedPage outOfRange = service.numberedList(query);
        assertEquals(4L, outOfRange.getTotalCount());
        assertEquals(0, outOfRange.getItems().size());
        assertEquals(2, outOfRange.getPagesAvailable());
    }
    
    @Test
    void numberedListShouldContinueCountingAfterRequestedPageIsFull() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(List.of(entry(1L, "alpha"), entry(2L, "bravo"),
                entry(3L, "charlie")));
        mockCurrentResources();
        Query query = listQuery();
        query.setPageSize(1);
        
        NumberedPage page = service.numberedList(query);
        
        assertEquals(3L, page.getTotalCount());
        assertEquals(3, page.getPagesAvailable());
        assertEquals(List.of("alpha"),
            List.of(page.getItems().get(0).getResourceName()));
    }
    
    @Test
    void numberedListShouldAdvanceAcrossBoundedResourceKeyBatches() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        List<AiResourceSearchDocument> firstBatch = new ArrayList<>();
        for (long id = 1L; id <= 500L; id++) {
            firstBatch.add(entry(id, String.format("skill-%03d", id - 1L)));
        }
        AiResourceSearchDocument finalDocument = entry(501L, "skill-500");
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(firstBatch);
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), "skill",
            "skill-499", 500L, 500)).thenReturn(List.of(finalDocument));
        mockCurrentResources();
        Query query = listQuery();
        query.setPageNumber(251);
        query.setPageSize(2);
        
        NumberedPage page = service.numberedList(query);
        
        assertEquals(501L, page.getTotalCount());
        assertEquals(251, page.getPagesAvailable());
        assertEquals(1, page.getItems().size());
        assertEquals("skill-500", page.getItems().get(0).getResourceName());
    }
    
    @Test
    void numberedListShouldHandleNullBatchAndRejectIncompleteAnchor() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        Query query = listQuery();
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(null, Collections.emptyList());
        NumberedPage empty = service.numberedList(query);
        assertEquals(0L, empty.getTotalCount());
        assertEquals(0, empty.getPagesAvailable());
        assertEquals(0L, service.numberedList(query).getTotalCount());
        
        AiResourceSearchDocument incomplete = entry(1L, "incomplete");
        incomplete.setId(null);
        incomplete.setStatus("disabled");
        AiResourceSearchDocument missingType = entry(1L, "missing-type");
        missingType.setResourceType(null);
        missingType.setStatus("disabled");
        AiResourceSearchDocument missingName = entry(1L, "missing-name");
        missingName.setResourceName(null);
        missingName.setStatus("disabled");
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(List.of(incomplete), List.of(missingType), List.of(missingName));
        assertThrows(IllegalStateException.class, () -> service.numberedList(query));
        assertThrows(IllegalStateException.class, () -> service.numberedList(query));
        assertThrows(IllegalStateException.class, () -> service.numberedList(query));
    }
    
    @Test
    void numberedListShouldRejectEveryNonAdvancingResourceKeyDimension() {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        Query query = listQuery();
        List<AiResourceSearchDocument> firstBatch = new ArrayList<>();
        for (long id = 1L; id <= 500L; id++) {
            AiResourceSearchDocument document = entry(id, String.format("skill-%03d", id - 1L));
            document.setStatus("disabled");
            firstBatch.add(document);
        }
        AiResourceSearchDocument typeBackwards = entry(501L, "z");
        typeBackwards.setResourceType("prompt");
        typeBackwards.setStatus("disabled");
        AiResourceSearchDocument nameBackwards = entry(501L, "skill-400");
        nameBackwards.setStatus("disabled");
        AiResourceSearchDocument idBackwards = entry(500L, "skill-499");
        idBackwards.setStatus("disabled");
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(firstBatch);
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), "skill",
            "skill-499", 500L, 500)).thenReturn(List.of(typeBackwards),
                List.of(nameBackwards), List.of(idBackwards));
        
        assertThrows(IllegalStateException.class, () -> service.numberedList(query));
        assertThrows(IllegalStateException.class, () -> service.numberedList(query));
        assertThrows(IllegalStateException.class, () -> service.numberedList(query));
    }
    
    @Test
    void numberedListShouldAcceptEveryAdvancingResourceKeyDimension() throws Exception {
        AiResourceSearchService service = new AiResourceSearchService(resourceManager,
            mcpServerOperationService, repository, embeddingService, vectorIndex);
        Query query = listQuery();
        List<AiResourceSearchDocument> firstBatch = new ArrayList<>();
        for (long id = 1L; id <= 500L; id++) {
            AiResourceSearchDocument document = entry(id, String.format("skill-%03d", id - 1L));
            document.setStatus("disabled");
            firstBatch.add(document);
        }
        AiResourceSearchDocument typeAdvance = entry(501L, "alpha");
        typeAdvance.setResourceType("tool");
        typeAdvance.setStatus("disabled");
        AiResourceSearchDocument nameAdvance = entry(501L, "skill-500");
        nameAdvance.setStatus("disabled");
        AiResourceSearchDocument idAdvance = entry(501L, "skill-499");
        idAdvance.setStatus("disabled");
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), null, null,
            0L, 500)).thenReturn(firstBatch, firstBatch, firstBatch);
        when(repository.scanEnabledEntriesByResourceKey("public", List.of("skill"), "skill",
            "skill-499", 500L, 500)).thenReturn(List.of(typeAdvance),
                List.of(nameAdvance), List.of(idAdvance));
        
        assertEquals(0L, service.numberedList(query).getTotalCount());
        assertEquals(0L, service.numberedList(query).getTotalCount());
        assertEquals(0L, service.numberedList(query).getTotalCount());
    }
    
    @Test
    void queryPredicateAndNumberedPageShouldNormalizeAndExposeValues() {
        Query query = new Query();
        query.setPageNumber(0);
        query.setPageSize(0);
        query.setPredicates(null);
        assertEquals(1, query.getPageNumber());
        assertEquals(1, query.getPageSize());
        assertTrue(query.getPredicates().isEmpty());
        
        Predicate predicate = new Predicate();
        predicate.setField("resourceName");
        predicate.setOperator(null);
        predicate.setValues(null);
        predicate.setCaseSensitive(true);
        assertEquals("resourceName", predicate.getField());
        assertEquals(PredicateOperator.EXACT_ANY, predicate.getOperator());
        assertTrue(predicate.getValues().isEmpty());
        assertTrue(predicate.isCaseSensitive());
        
        NumberedPage page = new NumberedPage(Collections.emptyList(), 3L, 2, 4);
        assertTrue(page.getItems().isEmpty());
        assertEquals(3L, page.getTotalCount());
        assertEquals(2, page.getPageNumber());
        assertEquals(4, page.getPagesAvailable());
    }
    
    private Query listQuery() {
        Query query = new Query();
        query.setNamespaceId("public");
        query.setResourceTypes(List.of("skill"));
        query.setLimit(10);
        return query;
    }
    
    private void mockCurrentResources() {
        when(resourceManager.findMeta(eq("public"), any(), eq("skill")))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(), eq("skill"), eq("1.0.0")))
            .thenReturn(onlineVersion());
    }
    
    private AiResourceSearchHit hit(long documentId) {
        AiResourceSearchHit hit = new AiResourceSearchHit();
        hit.setDocumentId(documentId);
        hit.setChunkId(documentId);
        hit.setChunkType("description");
        hit.setScore(1.0D);
        return hit;
    }
    
    private AiResourceSearchDocument entry(long id, String name) {
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setId(id);
        entry.setNamespaceId("public");
        entry.setResourceType("skill");
        entry.setResourceName(name);
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName(name);
        entry.setStatus("enabled");
        entry.setGmtModified(new Timestamp(id));
        return entry;
    }
    
    private AiResource meta(String latestVersion) {
        AiResource meta = new AiResource();
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setVersionInfo("{\"labels\":{\"" + AiResourceConstants.LABEL_LATEST
            + "\":\"" + latestVersion + "\"}}");
        return meta;
    }
    
    private AiResourceVersion onlineVersion() {
        AiResourceVersion version = new AiResourceVersion();
        version.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        return version;
    }
}
