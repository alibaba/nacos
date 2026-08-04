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

package com.alibaba.nacos.ai.remote.handler.agent;

import com.alibaba.nacos.ai.param.AgentClientRpcParamExtractor;
import com.alibaba.nacos.ai.service.agent.AgentPublishApplicationService;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentPublishRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentPublishRpcResponse;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

/**
 * Handles code-first Agent definition publication over gRPC.
 *
 * @author Nacos
 */
@Since("3.3.0")
@Component
public class AgentPublishRpcRequestHandler
    extends RequestHandler<AgentPublishRpcRequest, AgentPublishRpcResponse> {
    
    private final AgentPublishApplicationService publishService;
    
    public AgentPublishRpcRequestHandler(AgentPublishApplicationService publishService) {
        this.publishService = publishService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentClientRpcParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public AgentPublishRpcResponse handle(AgentPublishRpcRequest request, RequestMeta meta)
        throws NacosException {
        AgentPublishRpcResponse response = new AgentPublishRpcResponse();
        try {
            AgentPublishRequest publishRequest = requireRequest(request.getPublishRequest());
            String namespaceId = NamespaceUtil.processNamespaceParameter(request.getNamespaceId());
            response.setVersionDetail(publishService.publish(namespaceId, publishRequest));
        } catch (Exception e) {
            AgentGrpcResponseErrorMapper.apply(response, e);
        }
        return response;
    }
    
    private AgentPublishRequest requireRequest(AgentPublishRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("publishRequest must not be null");
        }
        return request;
    }
}
