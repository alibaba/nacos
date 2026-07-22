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

package com.alibaba.nacos.ai.service.ard.vector;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ArdVectorIndexRouter}.
 *
 * @author nacos
 */
class ArdVectorIndexRouterTest {
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void shouldUseConfiguredProvider() {
        FakeVectorIndex index = new FakeVectorIndex(true);
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(
            Map.of("custom", index), "custom");
        
        assertTrue(router.available());
        assertSame(index, router.delegate());
    }
    
    @Test
    void shouldFallbackToNoopWhenProviderMissing() {
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(Collections.emptyMap(), "missing");
        
        assertFalse(router.available());
        assertTrue(router.search("public", "model", new double[] {1.0D}, List.of("skill"), 10)
            .isEmpty());
    }
    
    @Test
    void shouldFallbackToNoopWhenConfiguredProviderIsDisabled() {
        FakeVectorIndex index = new FakeVectorIndex(true);
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(
            Map.of("custom", index), "custom");
        PluginStateCheckerHolder.setInstance(
            (pluginType, pluginName) -> !PluginType.AI_VECTOR.getType().equals(pluginType)
                || !"custom".equals(pluginName));
        
        assertFalse(router.available());
    }
    
    @Test
    void shouldCloseAllProvidersOnDestroy() throws Exception {
        FakeVectorIndex selected = new FakeVectorIndex(true);
        FakeVectorIndex other = new FakeVectorIndex(true);
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(
            Map.of("selected", selected, "other", other), "selected");
        
        router.available();
        router.destroy();
        
        assertTrue(selected.closed);
        assertTrue(other.closed);
    }
    
    private static class FakeVectorIndex implements AiResourceVectorIndex {
        
        private final boolean available;
        
        private boolean closed;
        
        private FakeVectorIndex(boolean available) {
            this.available = available;
        }
        
        @Override
        public boolean available() {
            return available;
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
        
        @Override
        public void close() {
            closed = true;
        }
    }
}
