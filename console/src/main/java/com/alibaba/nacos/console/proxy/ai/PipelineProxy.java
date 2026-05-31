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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.PipelineHandler;
import org.springframework.stereotype.Component;

/**
 * Pipeline proxy — delegates to PipelineHandler implementation.
 *
 * @author kiro
 * @since 3.2.0
 */
@Component
public class PipelineProxy {
    
    private final PipelineHandler pipelineHandler;
    
    public PipelineProxy(PipelineHandler pipelineHandler) {
        this.pipelineHandler = pipelineHandler;
    }
    
    public PipelineExecution getPipeline(String pipelineId) throws NacosException {
        return pipelineHandler.getPipeline(pipelineId);
    }
    
    public Page<PipelineExecution> listPipelines(String resourceType, String resourceName,
        String namespaceId, String version, int pageNo, int pageSize) throws NacosException {
        return pipelineHandler.listPipelines(resourceType, resourceName,
            namespaceId, version, pageNo, pageSize);
    }
}
