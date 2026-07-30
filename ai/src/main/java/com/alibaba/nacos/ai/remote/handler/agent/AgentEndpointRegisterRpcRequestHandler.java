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
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
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
 * Handles complete RAD Agent Endpoint registration over gRPC.
 *
 * @author Nacos
 */
@Since("3.3.0")
@Component
public class AgentEndpointRegisterRpcRequestHandler
    extends RequestHandler<AgentEndpointRegisterRpcRequest, AgentEndpointOperationResponse> {
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    public AgentEndpointRegisterRpcRequestHandler(
        AgentRuntimeRegistryService runtimeRegistryService) {
        this.runtimeRegistryService = runtimeRegistryService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentClientRpcParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public AgentEndpointOperationResponse handle(AgentEndpointRegisterRpcRequest request,
        RequestMeta meta) throws NacosException {
        AgentEndpointOperationResponse response = new AgentEndpointOperationResponse();
        try {
            AgentEndpointRegistrationBatch batch =
                requireRequest(request.getRegistrationBatch(), "registrationBatch");
            batch.setNamespaceId(NamespaceUtil.processNamespaceParameter(
                batch.getNamespaceId()));
            runtimeRegistryService.register(meta.getConnectionId(), batch);
        } catch (Exception e) {
            AgentGrpcResponseErrorMapper.apply(response, e);
        }
        return response;
    }
    
    private <T> T requireRequest(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
