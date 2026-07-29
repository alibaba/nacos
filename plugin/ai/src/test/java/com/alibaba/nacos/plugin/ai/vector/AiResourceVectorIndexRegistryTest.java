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

package com.alibaba.nacos.plugin.ai.vector;

import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndexBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiResourceVectorIndexRegistry}.
 *
 * @author nacos
 */
class AiResourceVectorIndexRegistryTest {
    
    @Test
    void shouldLoadVectorIndexPlugins() {
        FakeVectorIndex first = new FakeVectorIndex();
        FakeVectorIndex second = new FakeVectorIndex();
        AiResourceVectorIndexRegistry registry = new AiResourceVectorIndexRegistry(
            List.of(new FakeVectorIndexBuilder("first", first),
                new FakeVectorIndexBuilder("second", second)));
        
        assertSame(first, registry.getAllIndexes().get("first"));
        assertSame(second, registry.getAllIndexes().get("second"));
    }
    
    @Test
    void shouldRejectDuplicateProviderType() {
        assertThrows(IllegalStateException.class, () -> new AiResourceVectorIndexRegistry(
            List.of(new FakeVectorIndexBuilder("duplicate", new FakeVectorIndex()),
                new FakeVectorIndexBuilder("duplicate", new FakeVectorIndex()))));
    }
    
    @Test
    void shouldIgnoreProviderThatCannotBeBuilt() {
        AiResourceVectorIndexBuilder failedBuilder = new AiResourceVectorIndexBuilder() {
            
            @Override
            public String type() {
                return "failed";
            }
            
            @Override
            public AiResourceVectorIndex build() {
                throw new IllegalStateException("failed");
            }
        };
        AiResourceVectorIndexRegistry registry = new AiResourceVectorIndexRegistry(
            List.of(failedBuilder, new FakeVectorIndexBuilder("empty", null)));
        
        assertTrue(registry.getAllIndexes().isEmpty());
    }
    
    private static class FakeVectorIndexBuilder implements AiResourceVectorIndexBuilder {
        
        private final String type;
        
        private final AiResourceVectorIndex index;
        
        private FakeVectorIndexBuilder(String type, AiResourceVectorIndex index) {
            this.type = type;
            this.index = index;
        }
        
        @Override
        public String type() {
            return type;
        }
        
        @Override
        public AiResourceVectorIndex build() {
            return index;
        }
    }
    
    private static class FakeVectorIndex implements AiResourceVectorIndex {
        
        @Override
        public boolean available() {
            return true;
        }
        
        @Override
        public void replaceResourceVersion(String namespaceId, String resourceType,
            String resourceName, String resourceVersion,
            Collection<AiResourceVectorDocument> documents) {
        }
        
        @Override
        public void addDocuments(Collection<AiResourceVectorDocument> documents) {
        }
        
        @Override
        public void deleteByResource(String namespaceId, String resourceType,
            String resourceName) {
        }
        
        @Override
        public void deleteByResourceVersion(String namespaceId, String resourceType,
            String resourceName, String resourceVersion) {
        }
        
        @Override
        public List<AiResourceVectorHit> search(String namespaceId, String embeddingModel,
            double[] queryVector, List<String> resourceTypes, int limit) {
            return Collections.emptyList();
        }
    }
}
