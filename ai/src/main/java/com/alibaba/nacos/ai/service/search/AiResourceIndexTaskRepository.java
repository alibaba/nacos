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

import com.alibaba.nacos.ai.model.search.AiResourceIndexTask;

import java.sql.Timestamp;
import java.util.List;

/**
 * Repository for durable AI resource index maintenance tasks.
 *
 * @author nacos
 */
public interface AiResourceIndexTaskRepository {
    
    /**
     * Coalesce a resource change and its enhancement intent into one pending task.
     */
    void schedule(String namespaceId, String resourceType, String resourceName,
        boolean enhancementRequested);
    
    /**
     * Coalesce an inconsistent-index repair while preserving active task intent.
     */
    void scheduleReconciliation(String namespaceId, String resourceType, String resourceName,
        boolean enhancementRequested);
    
    /**
     * Find tasks whose retry or lease time has elapsed.
     */
    List<AiResourceIndexTask> findDueTasks(int limit);
    
    /**
     * Claim one task revision for exclusive processing.
     */
    boolean claim(AiResourceIndexTask task, Timestamp leaseUntil);
    
    /**
     * Renew the lease held by one processing task revision.
     */
    boolean renewLease(AiResourceIndexTask task, Timestamp leaseUntil);
    
    /**
     * Advance a completed base-index revision to durable enhancement.
     */
    boolean advanceToEnhancement(AiResourceIndexTask task);
    
    /**
     * Retain a completed checkpoint for the claimed stage and revision.
     */
    boolean complete(AiResourceIndexTask task, String enhancementFingerprint);
    
    /**
     * Remove a completed deletion task.
     */
    boolean remove(AiResourceIndexTask task);
    
    /**
     * Retain the claimed revision for a later retry.
     */
    void retry(AiResourceIndexTask task, Timestamp nextRetryTime, String lastError);
    
    /**
     * Release a replacement revision that was scheduled while this revision was processing.
     */
    void releaseSuperseded(AiResourceIndexTask task);
}
