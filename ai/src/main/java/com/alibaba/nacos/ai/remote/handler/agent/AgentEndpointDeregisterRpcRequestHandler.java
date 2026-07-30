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
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointOperationResponse;
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
 * Handles complete RAD Agent Endpoint publication removal over gRPC.
 *
 * @author Nacos
 */
@Since("3.3.0")
@Component
public class AgentEndpointDeregisterRpcRequestHandler
    extends RequestHandler<AgentEndpointDeregisterRpcRequest, AgentEndpointOperationResponse> {
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    public AgentEndpointDeregisterRpcRequestHandler(
        AgentRuntimeRegistryService runtimeRegistryService) {
        this.runtimeRegistryService = runtimeRegistryService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentClientRpcParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public AgentEndpointOperationResponse handle(AgentEndpointDeregisterRpcRequest request,
        RequestMeta meta) throws NacosException {
        AgentEndpointOperationResponse response = new AgentEndpointOperationResponse();
        try {
            request.setNamespaceId(
                NamespaceUtil.processNamespaceParameter(request.getNamespaceId()));
            runtimeRegistryService.deregisterPublisher(meta.getConnectionId(),
                request.getNamespaceId(), request.getAgentName(), request.getProtocol());
        } catch (Exception e) {
            AgentGrpcResponseErrorMapper.apply(response, e);
        }
        return response;
    }
}
