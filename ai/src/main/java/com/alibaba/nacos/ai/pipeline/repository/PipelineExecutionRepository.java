/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.pipeline.repository;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;

/**
 * Repository interface for pipeline execution record persistence.
 *
 * @author kiro
 * @since 3.2.0
 */
public interface PipelineExecutionRepository {
    
    /**
     * Insert a new pipeline execution record.
     *
     * @param execution the execution record to save
     */
    void save(PipelineExecution execution);
    
    /**
     * Update an existing pipeline execution record (status and pipeline JSON).
     *
     * @param execution the execution record to update
     */
    void update(PipelineExecution execution);
    
    /**
     * Find a pipeline execution record by execution ID.
     *
     * @param executionId the execution ID
     * @return the execution record, or null if not found
     */
    PipelineExecution findById(String executionId);
    
    /**
     * Find the most recent pipeline execution record by resource information.
     *
     * @param resourceType the resource type
     * @param resourceName the resource name
     * @param namespaceId  the namespace ID
     * @param version      the resource version
     * @return the most recent matching execution record, or null if not found
     */
    PipelineExecution findByResource(String resourceType, String resourceName, String namespaceId, String version);
}
