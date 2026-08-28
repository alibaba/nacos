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
     * Find search-index tasks whose execution or lease time has elapsed.
     */
    List<AiResourceIndexTask> findDueTasks(int limit);
    
    /**
     * Check whether one resource type still has pending, processing, or retry work.
     *
     * @param resourceType exact canonical resource type
     * @return {@code true} when unfinished work or an undecodable unfinished task exists
     */
    boolean hasUnfinishedTasks(String resourceType);
    
    /**
     * Claim one task revision for exclusive processing with a new lease token for the given
     * duration in milliseconds.
     */
    boolean claim(AiResourceIndexTask task, long leaseDurationMillis);
    
    /**
     * Renew the lease identified by the task's lease token for the given duration in
     * milliseconds.
     */
    boolean renewLease(AiResourceIndexTask task, long leaseDurationMillis);
    
    /**
     * Advance a completed base-index revision to durable enhancement.
     */
    boolean advanceToEnhancement(AiResourceIndexTask task);
    
    /**
     * Restart the claimed task revision from the base-index stage.
     *
     * @return {@code false} when the claimed revision has already been superseded
     */
    boolean restartFromBase(AiResourceIndexTask task, boolean enhancementRequested);
    
    /**
     * Retain a completed checkpoint for the claimed stage and revision.
     */
    boolean complete(AiResourceIndexTask task, String enhancementFingerprint);
    
    /**
     * Remove a completed deletion task.
     */
    boolean remove(AiResourceIndexTask task);
    
    /**
     * Retain the claimed revision for a retry after the given delay in milliseconds.
     */
    boolean retry(AiResourceIndexTask task, long retryDelayMillis, String lastError);
    
    /**
     * Release a replacement revision only while it still carries this worker's lease token.
     */
    void releaseSuperseded(AiResourceIndexTask task);
}
