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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.PipelineHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/**
 * Remote implementation of Pipeline handler.
 *
 * <p>Calls remote Nacos server through maintainer client for Pipeline operations.</p>
 *
 * @author kiro
 * @since 3.2.0
 */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class PipelineRemoteHandler implements PipelineHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public PipelineRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public PipelineExecution getPipeline(String pipelineId) throws NacosException {
        JsonNode jsonNode =
            clientHolder.getAiMaintainerService().pipeline().getPipeline(pipelineId);
        return JacksonUtils.toObj(jsonNode.toString(), PipelineExecution.class);
    }
    
    @Override
    public Page<PipelineExecution> listPipelines(String resourceType, String resourceName,
        String namespaceId, String version, int pageNo, int pageSize) throws NacosException {
        JsonNode jsonNode = clientHolder.getAiMaintainerService().pipeline()
            .listPipelines(resourceType, resourceName, namespaceId, version, pageNo, pageSize);
        return JacksonUtils.toObj(jsonNode.toString(),
            new TypeReference<Page<PipelineExecution>>() {
            });
    }
}
