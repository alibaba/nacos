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

import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.model.search.AiResourceSearchHit;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Page;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Query;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiResourceSearchRelevanceTest {
    
    private AiResourceSearchService service;
    private AiResourceRelevanceFilter filter;
    private AiResourceRelevanceProvider provider;
    private AiResourceSearchTypeHandlerRegistry registry;
    private Query query;
    
    @BeforeEach
    void setup() throws Exception {
        registry = mock(AiResourceSearchTypeHandlerRegistry.class);
        when(registry.isCurrent(any())).thenReturn(true);
        AiResourceSearchRepository repository = mock(AiResourceSearchRepository.class);
        List<AiResourceSearchDocument> documents = new ArrayList<>();
        List<AiResourceSearchHit> hits = new ArrayList<>();
        for (long id = 1; id <= 6; id++) {
            AiResourceSearchDocument document = new AiResourceSearchDocument();
            document.setId(id);
            document.setNamespaceId("public");
            document.setResourceType("skill");
            document.setResourceName("skill-" + id);
            document.setResourceVersion("1.0");
            document.setStatus("enabled");
            document.setDescription("capability");
            document.setMetadata("{\"secret\":\"must-not-send\"}");
            documents.add(document);
            AiResourceSearchHit hit = new AiResourceSearchHit();
            hit.setDocumentId(id);
            hit.setChunkId(id);
            hit.setChunkType("description");
            hit.setScore(1);
            hits.add(hit);
        }
        when(repository.searchChunks(anyString(), anyString(), any(), anyInt())).thenReturn(hits);
        when(repository.findEntriesByIds(any())).thenReturn(documents);
        service = new AiResourceSearchService(registry, repository,
            mock(AiResourceEmbeddingService.class),
            mock(AiResourceVectorIndex.class));
        provider = AiResourceRelevanceFilterTest.provider();
        filter = new AiResourceRelevanceFilter(AiResourceRelevanceFilterTest.configuration(
            Map.of("max-candidates", "3")), List.of(provider));
        service.setRelevanceFilter(filter);
        query = new Query();
        query.setNamespaceId("public");
        query.setText("query");
        query.setLimit(2);
        query.setRelevanceRequested(true);
    }
    
    @AfterEach
    void close() {
        filter.close();
    }
    
    private void scores(double score) throws Exception {
        when(provider.evaluateBatch(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            List<Map<String, String>> batch = invocation.getArgument(1);
            Map<String, Double> scores = new java.util.LinkedHashMap<>();
            batch.forEach(item -> {
                assertEquals(java.util.Set.of("id", "name", "type", "description", "capabilities"),
                    item.keySet());
                scores.put(item.get("id"), score);
            });
            return scores;
        });
    }
    
    @Test
    void emptyFilteredPageAdvancesByWindowAndTerminates() throws Exception {
        scores(0.1);
        Page first = service.search(query);
        assertEquals("applied", first.getEnhancementStatus());
        assertTrue(first.getItems().isEmpty());
        assertNotNull(first.getNextCursor());
        query.setCursor(first.getNextCursor());
        Page second = service.search(query);
        assertTrue(second.getItems().isEmpty());
        assertNull(second.getNextCursor());
    }
    
    @Test
    void filledPagePreservesScoresOrderAndDoesNotSkipUnconsumedCandidates() throws Exception {
        scores(0.5);
        query.setRelevanceRequested(false);
        Page baseline = service.search(query);
        query.setRelevanceRequested(true);
        Page first = service.search(query);
        assertEquals(baseline.getItems().get(0).getResourceName(),
            first.getItems().get(0).getResourceName());
        assertEquals(baseline.getItems().get(0).getScore(), first.getItems().get(0).getScore());
        query.setCursor(first.getNextCursor());
        assertEquals("skill-3", service.search(query).getItems().get(0).getResourceName());
        query.setRelevanceRequested(false);
        assertThrows(NacosException.class, () -> service.search(query));
        query.setRelevanceRequested(true);
        query.setText("changed");
        assertThrows(NacosException.class, () -> service.search(query));
        query.setText("query");
        query.setCursor(baseline.getNextCursor());
        assertThrows(NacosException.class, () -> service.search(query));
    }
    
    @Test
    void failedAssessmentReturnsBasicPageWithCorrectContinuation() throws Exception {
        when(provider.evaluateBatch(any(), any(), any(), anyLong()))
            .thenThrow(new IllegalStateException("failed"));
        Page first = service.search(query);
        assertEquals("bypassed", first.getEnhancementStatus());
        assertEquals(2, first.getItems().size());
        query.setCursor(first.getNextCursor());
        assertEquals("skill-3", service.search(query).getItems().get(0).getResourceName());
    }
    
    @Test
    void rechecksEligibilityAfterExternalCallAndDoesNotSwallowCoreFailure() throws Exception {
        AtomicBoolean evaluated = new AtomicBoolean();
        when(registry.isCurrent(any())).thenAnswer(invocation -> !evaluated.get());
        when(provider.evaluateBatch(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            evaluated.set(true);
            return Map.of("candidate_0", 1.0, "candidate_1", 1.0, "candidate_2", 1.0);
        });
        assertTrue(service.search(query).getItems().isEmpty());
        when(registry.isCurrent(any())).thenThrow(new NacosException(500, "core failure"));
        assertThrows(NacosException.class, () -> service.search(query));
    }
    
    @Test
    void changedConfigurationInvalidatesCursorAndDisabledConfigurationSkipsProvider()
        throws Exception {
        scores(0.9);
        query.setCursor(service.search(query).getNextCursor());
        filter.close();
        filter = new AiResourceRelevanceFilter(AiResourceRelevanceFilterTest.configuration(
            Map.of("threshold", "0.6", "max-candidates", "3")), List.of(provider));
        service.setRelevanceFilter(filter);
        assertThrows(NacosException.class, () -> service.search(query));
        filter.close();
        filter = new AiResourceRelevanceFilter(new AiResourceRelevanceConfiguration(key -> null),
            List.of(provider));
        service.setRelevanceFilter(filter);
        query.setCursor(null);
        org.mockito.Mockito.clearInvocations(provider);
        assertEquals("disabled", service.search(query).getEnhancementStatus());
        verify(provider, never()).evaluateBatch(any(), any(), any(), anyLong());
    }
    
    @Test
    void realHttpProviderFiltersSearchWindowThroughAllProductionLayers() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger calls =
            new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/systemone", exchange -> {
            calls.incrementAndGet();
            com.fasterxml.jackson.databind.JsonNode body =
                com.alibaba.nacos.common.utils.JacksonUtils.toObj(
                    new String(exchange.getRequestBody().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8));
            Map<String, Object> answers = new java.util.LinkedHashMap<>();
            body.path("questions").fieldNames().forEachRemaining(
                id -> answers.put(id, Map.of("noul", id.equals("candidate_1") ? 0.9 : 0.1)));
            byte[] result =
                com.alibaba.nacos.common.utils.JacksonUtils.toJson(Map.of("answers", answers))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        filter.close();
        filter = new AiResourceRelevanceFilter(AiResourceRelevanceFilterTest.configuration(Map.of(
            "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone",
            "max-candidates", "3", "batch-size", "1", "batch-concurrency", "3")),
            List.of(new JevRelevanceProvider()));
        service.setRelevanceFilter(filter);
        try {
            Page page = service.search(query);
            assertEquals("applied", page.getEnhancementStatus());
            assertEquals(3, calls.get());
            assertEquals(1, page.getItems().size());
            assertEquals("skill-2", page.getItems().get(0).getResourceName());
            assertNotNull(page.getNextCursor());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void optOutDoesNotInvokeProvider() throws Exception {
        query.setRelevanceRequested(false);
        assertEquals("disabled", service.search(query).getEnhancementStatus());
        verify(provider, never()).evaluateBatch(any(), any(), any(), anyLong());
    }
}
