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
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiResourceIndexProjectionBuilder}.
 *
 * @author nacos
 */
class AiResourceIndexProjectionBuilderTest {
    
    @Test
    void buildShouldSeparateDocumentChunksAndFacets() {
        AiResourceSearchDocument document = document();
        document.setMetadata(JacksonUtils.toJson(Map.of("scope", "public",
            "protocols", List.of("a2a"))));
        List<AiResourceIndexEnhancementContent> contents = new ArrayList<>();
        contents.add(new AiResourceIndexEnhancementContent("agent.json",
            "Research and citation assistant"));
        
        AiResourceIndexProjection projection = new AiResourceIndexProjectionBuilder().build(
            document, contents, "agent_content");
        contents.clear();
        
        assertEquals(document, projection.getDocument());
        assertEquals("public", projection.getFacets().get("scope"));
        assertEquals(1, projection.getEnhancementContents().size());
        assertTrue(projection.getChunks().stream()
            .anyMatch(chunk -> "agent_content".equals(chunk.getChunkType())));
        assertThrows(UnsupportedOperationException.class,
            () -> projection.getFacets().put("owner", "nacos"));
        assertThrows(UnsupportedOperationException.class,
            () -> projection.getChunks().clear());
    }
    
    @Test
    void buildShouldTolerateMissingOrInvalidFacetsAndContent() {
        AiResourceSearchDocument document = document();
        AiResourceIndexProjection withoutMetadata =
            new AiResourceIndexProjectionBuilder().build(document, null, null);
        assertTrue(withoutMetadata.getFacets().isEmpty());
        assertTrue(withoutMetadata.getEnhancementContents().isEmpty());
        assertFalse(withoutMetadata.getChunks().isEmpty());
        
        document.setMetadata("not-json");
        AiResourceIndexProjection invalidMetadata =
            new AiResourceIndexProjectionBuilder().build(document,
                Arrays.asList(null,
                    new AiResourceIndexEnhancementContent("agent.json", "agent content")),
                "content");
        assertTrue(invalidMetadata.getFacets().isEmpty());
        assertTrue(invalidMetadata.getChunks().stream()
            .anyMatch(chunk -> "content".equals(chunk.getChunkType())));
        document.setMetadata("null");
        assertTrue(new AiResourceIndexProjectionBuilder().build(document, null, null)
            .getFacets().isEmpty());
        assertThrows(NullPointerException.class,
            () -> new AiResourceIndexProjection(null, null, null, null));
    }
    
    @Test
    void sourceAndPageShouldRetainBoundedReconciliationState() {
        AiResourceIndexProjection projection = new AiResourceIndexProjectionBuilder().build(
            document(), null, null);
        AiResourceIndexSource success = AiResourceIndexSource.success("research", projection);
        IllegalStateException failure = new IllegalStateException("projection failed");
        AiResourceIndexSource failed = AiResourceIndexSource.failed("broken", failure);
        List<AiResourceIndexSource> items = new ArrayList<>(List.of(success, failed));
        AiResourceIndexSourcePage page = new AiResourceIndexSourcePage(items, true);
        items.clear();
        
        assertEquals(2, page.getItems().size());
        assertTrue(page.hasMore());
        assertEquals(projection, success.getProjection());
        assertNull(success.getFailure());
        assertEquals(failure, failed.getFailure());
        assertNull(failed.getProjection());
        assertThrows(UnsupportedOperationException.class, () -> page.getItems().clear());
        assertFalse(new AiResourceIndexSourcePage(null, false).hasMore());
    }
    
    private AiResourceSearchDocument document() {
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setNamespaceId("public");
        document.setResourceType("agent");
        document.setResourceName("research");
        document.setResourceVersion("1.0.0");
        document.setDisplayName("Research Agent");
        document.setDescription("Research assistant");
        return document;
    }
}
