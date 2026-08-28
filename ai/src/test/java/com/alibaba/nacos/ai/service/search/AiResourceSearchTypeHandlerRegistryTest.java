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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiResourceSearchTypeHandlerRegistry}.
 *
 * @author nacos
 */
class AiResourceSearchTypeHandlerRegistryTest {
    
    @Test
    void registryShouldIndexAllOwnedTypesOnce() throws Exception {
        TestHandler handler = new TestHandler(List.of("skill", "prompt"), true);
        AiResourceSearchTypeHandlerRegistry registry =
            new AiResourceSearchTypeHandlerRegistry(Arrays.asList(null, handler));
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setResourceType("skill");
        
        assertSame(handler, registry.get("skill"));
        assertSame(handler, registry.get("prompt"));
        assertNull(registry.get("agent"));
        assertEquals(List.of("skill", "prompt"), List.copyOf(registry.resourceTypes()));
        assertEquals(List.of(handler), registry.handlers());
        assertTrue(registry.isCurrent(document));
        assertFalse(registry.isCurrent(null));
        document.setResourceType("agent");
        assertFalse(registry.isCurrent(document));
        assertTrue(new AiResourceSearchTypeHandlerRegistry(null).handlers().isEmpty());
    }
    
    @Test
    void registryShouldRejectInvalidOrDuplicateOwnership() {
        TestHandler skill = new TestHandler(List.of("skill"), true);
        TestHandler duplicate = new TestHandler(List.of("skill"), false);
        
        assertThrows(IllegalStateException.class,
            () -> new AiResourceSearchTypeHandlerRegistry(List.of(skill, duplicate)));
        assertThrows(IllegalStateException.class,
            () -> new AiResourceSearchTypeHandlerRegistry(
                List.of(new TestHandler(Collections.emptyList(), true))));
        assertThrows(IllegalStateException.class,
            () -> new AiResourceSearchTypeHandlerRegistry(
                List.of(new TestHandler(null, true))));
        assertThrows(IllegalStateException.class,
            () -> new AiResourceSearchTypeHandlerRegistry(
                List.of(new TestHandler(List.of(" "), true))));
        TestHandler repeated = new TestHandler(Arrays.asList("skill", "skill"), true);
        assertSame(repeated,
            new AiResourceSearchTypeHandlerRegistry(List.of(repeated)).get("skill"));
    }
    
    private static class TestHandler implements AiResourceSearchTypeHandler {
        
        private final Collection<String> resourceTypes;
        
        private final boolean current;
        
        private TestHandler(Collection<String> resourceTypes, boolean current) {
            this.resourceTypes = resourceTypes;
            this.current = current;
        }
        
        @Override
        public Collection<String> resourceTypes() {
            return resourceTypes;
        }
        
        @Override
        public AiResourceIndexProjection project(String namespaceId, String resourceType,
            String resourceName, String version) {
            return null;
        }
        
        @Override
        public AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
            int pageSize) {
            return new AiResourceIndexSourcePage(null, false);
        }
        
        @Override
        public boolean isCurrent(AiResourceSearchDocument document) {
            return current;
        }
        
        @Override
        public boolean exists(String namespaceId, String resourceType, String resourceName) {
            return false;
        }
    }
}
