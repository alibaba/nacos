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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.model.ard.ArdSearchHit;

import java.util.Collection;
import java.util.List;

/**
 * Persistent ARD entry and chunk repository.
 *
 * @author nacos
 */
public interface ArdIndexRepository {
    
    /**
     * Replace one resource-version entry and all derived chunks.
     *
     * @param entry ARD entry to persist
     * @param chunks derived chunks
     * @return chunks with generated ids
     */
    List<ArdChunk> replaceEntry(ArdEntry entry, List<ArdChunk> chunks);
    
    /**
     * Append derived chunks to an existing entry.
     *
     * @param entry existing ARD entry
     * @param chunks derived chunks
     * @return chunks with generated ids
     */
    List<ArdChunk> appendChunks(ArdEntry entry, List<ArdChunk> chunks);
    
    /**
     * Update one persisted entry status.
     */
    void updateEntryStatus(long entryId, String status);
    
    /**
     * Delete all ARD index rows for a resource.
     */
    void deleteByResource(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Delete all ARD index rows for one resource version.
     */
    void deleteByResourceVersion(String namespaceId, String resourceType, String resourceName,
        String resourceVersion);
    
    /**
     * Find the current entry for a resource.
     */
    ArdEntry findEntry(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Find entries by generated entry ids.
     */
    List<ArdEntry> findEntriesByIds(Collection<Long> entryIds);
    
    /**
     * Keyword search over persisted chunks.
     */
    List<ArdSearchHit> searchChunks(String namespaceId, String text, List<String> resourceTypes,
        int limit);
    
    /**
     * List enabled entries for rebuild or tests.
     */
    List<ArdEntry> listEnabledEntries(String namespaceId, List<String> resourceTypes, int limit);
    
    /**
     * List entries of any status for reconciliation.
     */
    List<ArdEntry> listEntries(String namespaceId, List<String> resourceTypes, int limit);
    
    /**
     * Count relational chunks for vector completeness checks.
     */
    int countChunks(long entryId);
}
