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

import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndexBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ArdVectorIndexRouter}.
 *
 * @author nacos
 */
class ArdVectorIndexRouterTest {

    @Test
    void shouldUseConfiguredProvider() {
        FakeVectorIndex index = new FakeVectorIndex(true);
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(
            List.of(new FakeVectorIndexBuilder("custom", index)), "custom");

        assertTrue(router.available());
        assertSame(index, router.delegate());
    }

    @Test
    void shouldFallbackToNoopWhenProviderMissing() {
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(Collections.emptyList(), "missing");

        assertFalse(router.available());
        assertTrue(router.search("public", "model", new double[] {1.0D}, List.of("skill"), 10)
            .isEmpty());
    }

    @Test
    void shouldRejectDuplicateProviderType() {
        FakeVectorIndex index = new FakeVectorIndex(true);

        assertThrows(IllegalStateException.class, () -> new ArdVectorIndexRouter(
            List.of(new FakeVectorIndexBuilder("custom", index),
                new FakeVectorIndexBuilder("custom", index)), "custom"));
    }

    @Test
    void shouldCloseSelectedProviderOnDestroy() throws Exception {
        FakeVectorIndex index = new FakeVectorIndex(true);
        ArdVectorIndexRouter router = new ArdVectorIndexRouter(
            List.of(new FakeVectorIndexBuilder("custom", index)), "custom");

        router.available();
        router.destroy();

        assertTrue(index.closed);
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
