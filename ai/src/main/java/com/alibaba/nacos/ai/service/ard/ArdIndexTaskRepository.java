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

import com.alibaba.nacos.ai.model.ard.ArdIndexTask;

import java.sql.Timestamp;
import java.util.List;

/**
 * Repository for durable ARD index maintenance tasks.
 *
 * @author nacos
 */
public interface ArdIndexTaskRepository {
    
    /**
     * Coalesce a resource change into one pending task.
     */
    void schedule(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Find tasks whose retry or lease time has elapsed.
     */
    List<ArdIndexTask> findDueTasks(int limit);
    
    /**
     * Claim one task revision for exclusive processing.
     */
    boolean claim(ArdIndexTask task, Timestamp leaseUntil);
    
    /**
     * Complete the claimed revision.
     */
    void complete(ArdIndexTask task);
    
    /**
     * Retain the claimed revision for a later retry.
     */
    void retry(ArdIndexTask task, Timestamp nextRetryTime, String lastError);
}
