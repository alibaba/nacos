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
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Page;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
