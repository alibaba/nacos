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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Handler interface for Pipeline query operations in Console layer.
 *
 * @author kiro
 * @since 3.2.0
 */
public interface PipelineHandler {
    
    /**
     * Get pipeline execution detail by ID.
     *
     * @param pipelineId the pipeline execution ID
     * @return the pipeline execution
     * @throws NacosException if query fails
     */
    PipelineExecution getPipeline(String pipelineId) throws NacosException;
    
    /**
     * List pipeline executions with pagination.
     *
     * @param resourceType the resource type (required)
     * @param resourceName the resource name (optional)
     * @param namespaceId  the namespace ID (optional)
     * @param version      the version (optional)
     * @param pageNo       the page number
     * @param pageSize     the page size
     * @return paginated results
     * @throws NacosException if query fails
     */
    Page<PipelineExecution> listPipelines(String resourceType, String resourceName,
        String namespaceId, String version, int pageNo, int pageSize) throws NacosException;
}
