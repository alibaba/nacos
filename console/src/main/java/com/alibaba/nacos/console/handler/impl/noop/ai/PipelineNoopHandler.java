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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.PipelineHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Noop implementation of Pipeline handler.
 * Used when AI module is not enabled.
 *
 * @author kiro
 * @since 3.2.0
 */
@Service
@ConditionalOnMissingBean(value = PipelineHandler.class, ignored = PipelineNoopHandler.class)
public class PipelineNoopHandler implements PipelineHandler {
    
    private static final String NOT_ENABLED_MSG =
        "Nacos AI Pipeline module requires both `naming` and `config` module.";
    
    @Override
    public PipelineExecution getPipeline(String pipelineId) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, NOT_ENABLED_MSG);
    }
    
    @Override
    public Page<PipelineExecution> listPipelines(String resourceType, String resourceName,
        String namespaceId, String version, int pageNo, int pageSize) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, NOT_ENABLED_MSG);
    }
}
