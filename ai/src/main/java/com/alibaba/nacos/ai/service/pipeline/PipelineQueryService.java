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

package com.alibaba.nacos.ai.service.pipeline;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer for Pipeline query operations.
 *
 * @author kiro
 * @since 3.2.0
 */
@Service
public class PipelineQueryService {
    
    private final PipelineExecutionRepository repository;
    
    public PipelineQueryService(PipelineExecutionRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Get a single pipeline execution by ID.
     *
     * @param pipelineId the execution ID
     * @return the pipeline execution
     * @throws NacosException if not found
     */
    public PipelineExecution getPipeline(String pipelineId) throws NacosException {
        PipelineExecution execution = repository.findById(pipelineId);
        if (execution == null) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                "Pipeline execution not found: " + pipelineId);
        }
        return execution;
    }
    
    /**
     * List pipeline executions with pagination.
     *
     * @param resourceType the resource type (required)
     * @param resourceName the resource name (optional)
     * @param namespaceId  the namespace ID (optional)
     * @param version      the version (optional)
     * @param pageNo       the page number (1-based)
     * @param pageSize     the page size
     * @return paginated results
     * @throws NacosException if query fails
     */
    public Page<PipelineExecution> listPipelines(String resourceType, String resourceName,
        String namespaceId, String version, int pageNo, int pageSize) throws NacosException {
        int offset = (pageNo - 1) * pageSize;
        List<PipelineExecution> list = repository.findByResourceWithPage(resourceType, resourceName,
            namespaceId, version, offset, pageSize);
        int totalCount =
            repository.countByResource(resourceType, resourceName, namespaceId, version);
        
        Page<PipelineExecution> page = new Page<>();
        page.setTotalCount(totalCount);
        page.setPageNumber(pageNo);
        page.setPagesAvailable((totalCount + pageSize - 1) / pageSize);
        page.setPageItems(list);
        return page;
    }
}
