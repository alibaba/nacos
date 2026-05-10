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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.service.pipeline.PipelineQueryService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.PipelineHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Component;

/**
 * Pipeline inner handler — directly delegates to PipelineQueryService.
 *
 * @author kiro
 * @since 3.2.0
 */
@Component
@EnabledInnerHandler
@EnabledAiHandler
public class PipelineInnerHandler implements PipelineHandler {
    
    private final PipelineQueryService pipelineQueryService;
    
    public PipelineInnerHandler(PipelineQueryService pipelineQueryService) {
        this.pipelineQueryService = pipelineQueryService;
    }
    
    @Override
    public PipelineExecution getPipeline(String pipelineId) throws NacosException {
        return pipelineQueryService.getPipeline(pipelineId);
    }
    
    @Override
    public Page<PipelineExecution> listPipelines(String resourceType, String resourceName,
        String namespaceId, String version, int pageNo, int pageSize) throws NacosException {
        return pipelineQueryService.listPipelines(resourceType, resourceName,
            namespaceId, version, pageNo, pageSize);
    }
}
