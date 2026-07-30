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

package com.alibaba.nacos.plugin.ai.vector.spi;

import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorHit;

import java.util.Collection;
import java.util.List;

/**
 * Vector index SPI for AI resource search chunks.
 *
 * @author nacos
 */
public interface AiResourceVectorIndex extends AutoCloseable {
    
    /**
     * Whether this vector index is usable for current runtime.
     */
    boolean available();
    
    /**
     * Replace embeddings for one resource version.
     */
    void replaceResourceVersion(String namespaceId, String resourceType, String resourceName,
        String resourceVersion, Collection<AiResourceVectorDocument> documents);
    
    /**
     * Add embeddings for newly appended chunks.
     */
    void addDocuments(Collection<AiResourceVectorDocument> documents);
    
    /**
     * Delete embeddings for a resource.
     */
    void deleteByResource(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Delete embeddings for one resource version.
     */
    void deleteByResourceVersion(String namespaceId, String resourceType, String resourceName,
        String resourceVersion);
    
    /**
     * Verify that one resource version contains the expected model and document count.
     *
     * <p>The default preserves compatibility with providers that do not expose
     * reconciliation metadata.</p>
     */
    default boolean isResourceVersionReady(String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String embeddingModel,
        int expectedDocumentCount) {
        return true;
    }
    
    /**
     * Verify the expected relational document identity in addition to model and count.
     *
     * <p>The default delegates to the original readiness method to preserve compatibility
     * with existing providers.</p>
     */
    default boolean isResourceVersionReady(String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String embeddingModel,
        long expectedDocumentId, int expectedDocumentCount) {
        return isResourceVersionReady(namespaceId, resourceType, resourceName, resourceVersion,
            embeddingModel, expectedDocumentCount);
    }
    
    /**
     * Vector search over AI resource search chunks.
     */
    List<AiResourceVectorHit> search(String namespaceId, String embeddingModel,
        double[] queryVector, List<String> resourceTypes, int limit);
    
    @Override
    default void close() throws Exception {
    }
}
