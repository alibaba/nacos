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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Nacos AI module Pipeline relative maintainer service.
 *
 * <p>Extends {@link PipelineAdminClient} for {@link com.alibaba.nacos.api.model.v2.Result} responses.
 * Legacy {@link JsonNode}-only accessors are retained for existing callers.</p>
 *
 * <p>Returns {@link JsonNode} on deprecated methods because {@code PipelineExecution} resides in the ai module,
 * which is not a compile-time dependency of maintainer-client. Callers should deserialize the JsonNode
 * to the concrete type.</p>
 *
 * @author kiro
 * @since 3.2.0
 */
public interface PipelineMaintainerService extends PipelineAdminClient {
    
    /**
     * Get pipeline execution detail by ID.
     *
     * @param pipelineId the pipeline execution ID
     * @return JSON representation of the pipeline execution data field on success
     * @throws NacosException if the request fails or the server returns a non-success Result
     * @deprecated since 3.2.1 use {@link #getPipelineDetail(String)} to handle {@code Result} explicitly
     */
    @Since("3.2.0")
    @Deprecated
    JsonNode getPipeline(String pipelineId) throws NacosException;
    
    /**
     * List pipeline executions with pagination.
     *
     * @param resourceType the resource type (required)
     * @param resourceName the resource name (optional)
     * @param namespaceId  the namespace ID (optional)
     * @param version      the version (optional)
     * @param pageNo       the page number
     * @param pageSize     the page size
     * @return JSON representation of the page data field on success
     * @throws NacosException if the request fails or the server returns a non-success Result
     * @deprecated since 3.2.1 use {@link #listPipelineExecutions(String, String, String, String, int, int)}
     */
    @Since("3.2.0")
    @Deprecated
    JsonNode listPipelines(String resourceType, String resourceName, String namespaceId,
        String version, int pageNo, int pageSize) throws NacosException;
}
