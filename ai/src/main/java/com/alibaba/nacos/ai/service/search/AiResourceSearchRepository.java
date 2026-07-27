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

import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.model.search.AiResourceSearchHit;

import java.util.Collection;
import java.util.List;

/**
 * Persistent search document and chunk repository.
 *
 * @author nacos
 */
public interface AiResourceSearchRepository {
    
    /**
     * Replace one resource-version entry and all derived chunks.
     *
     * @param entry search document to persist
     * @param chunks derived chunks
     * @return chunks with generated ids
     */
    List<AiResourceSearchChunk> replaceEntry(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> chunks);
    
    /**
     * Append derived chunks to an existing entry.
     *
     * @param entry existing search document
     * @param chunks derived chunks
     * @return chunks with generated ids
     */
    List<AiResourceSearchChunk> appendChunks(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> chunks);
    
    /**
     * Update one persisted entry status.
     */
    void updateEntryStatus(long documentId, String status);
    
    /**
     * Delete all AI resource index rows for a resource.
     */
    void deleteByResource(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Delete all AI resource index rows for one resource version.
     */
    void deleteByResourceVersion(String namespaceId, String resourceType, String resourceName,
        String resourceVersion);
    
    /**
     * Find the current entry for a resource.
     */
    AiResourceSearchDocument findEntry(String namespaceId, String resourceType,
        String resourceName);
    
    /**
     * Find entries by generated entry ids.
     */
    List<AiResourceSearchDocument> findEntriesByIds(Collection<Long> documentIds);
    
    /**
     * Keyword search over persisted chunks.
     */
    List<AiResourceSearchHit> searchChunks(String namespaceId, String text,
        List<String> resourceTypes,
        int limit);
    
    /**
     * List enabled entries for rebuild or tests.
     */
    List<AiResourceSearchDocument> listEnabledEntries(String namespaceId,
        List<String> resourceTypes, int limit);
    
    /**
     * List entries of any status for reconciliation.
     */
    List<AiResourceSearchDocument> listEntries(String namespaceId, List<String> resourceTypes,
        int limit);
    
    /**
     * Count relational chunks for vector completeness checks.
     */
    int countChunks(long documentId);
}
