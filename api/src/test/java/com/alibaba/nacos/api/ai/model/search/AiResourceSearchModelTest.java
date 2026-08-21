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

package com.alibaba.nacos.api.ai.model.search;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for public AI Resource Search models.
 */
class AiResourceSearchModelTest {
    
    @Test
    void itemShouldExposeSearchFieldsAndNormalizeNullCollections() {
        AiResourceSearchItem item = new AiResourceSearchItem();
        item.setNamespaceId("public");
        item.setResourceType("skill");
        item.setResourceName("research");
        item.setResourceVersion("1.0.0");
        item.setDisplayName("Research");
        item.setDescription("description");
        item.setTags(List.of("tag"));
        item.setCapabilities(List.of("tool"));
        item.setRepresentativeQueries(List.of("find papers"));
        item.setMetadata(Map.of("owner", "nacos"));
        item.setCreateTime(1L);
        item.setUpdateTime(2L);
        item.setScore(99);
        
        assertEquals("public", item.getNamespaceId());
        assertEquals("skill", item.getResourceType());
        assertEquals("research", item.getResourceName());
        assertEquals("1.0.0", item.getResourceVersion());
        assertEquals("Research", item.getDisplayName());
        assertEquals("description", item.getDescription());
        assertEquals(List.of("tag"), item.getTags());
        assertEquals(List.of("tool"), item.getCapabilities());
        assertEquals(List.of("find papers"), item.getRepresentativeQueries());
        assertEquals(Map.of("owner", "nacos"), item.getMetadata());
        assertEquals(1L, item.getCreateTime());
        assertEquals(2L, item.getUpdateTime());
        assertEquals(99, item.getScore());
        
        item.setTags(null);
        item.setCapabilities(null);
        item.setRepresentativeQueries(null);
        item.setMetadata(null);
        assertTrue(item.getTags().isEmpty());
        assertTrue(item.getCapabilities().isEmpty());
        assertTrue(item.getRepresentativeQueries().isEmpty());
        assertTrue(item.getMetadata().isEmpty());
    }
    
    @Test
    void responseShouldExposeCursorPageAndNormalizeNullItems() {
        AiResourceSearchResponse response = new AiResourceSearchResponse();
        AiResourceSearchItem item = new AiResourceSearchItem();
        response.setItems(Collections.singletonList(item));
        response.setNextCursor("next");
        
        assertEquals(Collections.singletonList(item), response.getItems());
        assertEquals("next", response.getNextCursor());
        
        response.setItems(null);
        assertTrue(response.getItems().isEmpty());
    }
}
