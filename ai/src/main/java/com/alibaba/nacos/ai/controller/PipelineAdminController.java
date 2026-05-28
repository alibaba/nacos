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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.pipeline.PipelineDetailForm;
import com.alibaba.nacos.ai.form.pipeline.PipelineListForm;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.service.pipeline.PipelineQueryService;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pipeline Admin Controller for querying pipeline execution records.
 *
 * @author kiro
 * @since 3.2.0
 */
@NacosApi
@RestController
@RequestMapping(Constants.Pipeline.ADMIN_PATH)
public class PipelineAdminController {
    
    private final PipelineQueryService pipelineQueryService;
    
    public PipelineAdminController(PipelineQueryService pipelineQueryService) {
        this.pipelineQueryService = pipelineQueryService;
    }
    
    /**
     * List pipeline executions with pagination.
     */
    @Since("3.2.0")
    @GetMapping(Constants.Pipeline.LIST_SUBPATH)
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<PipelineExecution>> listPipelines(PipelineListForm form, PageForm pageForm)
        throws NacosException {
        form.validate();
        pageForm.validate();
        return Result.success(
            pipelineQueryService.listPipelines(form.getResourceType(), form.getResourceName(),
                form.getNamespaceId(), form.getVersion(), pageForm.getPageNo(),
                pageForm.getPageSize()));
    }
    
    /**
     * Get pipeline execution detail by ID (query parameter {@code pipelineId}).
     */
    @Since("3.2.1")
    @GetMapping(Constants.Pipeline.DETAIL_SUBPATH)
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<PipelineExecution> getPipelineDetail(PipelineDetailForm form)
        throws NacosException {
        form.validate();
        return Result.success(pipelineQueryService.getPipeline(form.getPipelineId()));
    }
    
    /**
     * Get pipeline execution detail by ID in path.
     *
     * @deprecated since 3.2.1, for removal in a future release. Use {@code GET .../detail?pipelineId=}.
     */
    @Since("3.2.0")
    @Deprecated(since = "3.2.1", forRemoval = true)
    @GetMapping("/{pipelineId}")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<PipelineExecution> getPipeline(@PathVariable String pipelineId)
        throws NacosException {
        return Result.success(pipelineQueryService.getPipeline(pipelineId));
    }
    
    /**
     * List pipeline executions with pagination at the controller base path.
     *
     * @deprecated since 3.2.1, for removal in a future release. Use {@code GET .../list}.
     */
    @Since("3.2.1")
    @Deprecated(since = "3.2.1", forRemoval = true)
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<PipelineExecution>> listPipelinesLegacy(PipelineListForm form,
        PageForm pageForm)
        throws NacosException {
        form.validate();
        pageForm.validate();
        return Result.success(
            pipelineQueryService.listPipelines(form.getResourceType(), form.getResourceName(),
                form.getNamespaceId(), form.getVersion(), pageForm.getPageNo(),
                pageForm.getPageSize()));
    }
}
