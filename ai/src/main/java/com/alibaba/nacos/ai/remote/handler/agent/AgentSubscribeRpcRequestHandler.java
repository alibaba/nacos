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
import com.alibaba.nacos.ai.service.agent.watch.AgentGrpcWatchService;
import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.remote.request.AgentSubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
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
 * Installs one connection-owned RAD Watch over gRPC.
 *
 * @author Nacos
 */
@Since("3.4.0")
@Component
public class AgentSubscribeRpcRequestHandler
    extends RequestHandler<AgentSubscribeRpcRequest, AgentSubscribeRpcResponse> {
    
    private final AgentGrpcWatchService watchService;
    
    public AgentSubscribeRpcRequestHandler(AgentGrpcWatchService watchService) {
        this.watchService = watchService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentClientRpcParamExtractor.class)
    @Secured(action = ActionTypes.READ, signType = SignType.AI)
    public AgentSubscribeRpcResponse handle(AgentSubscribeRpcRequest request, RequestMeta meta)
        throws NacosException {
        try {
            requireWatchAbility(meta);
            if (request.getDiscoveryRequest() == null) {
                throw new IllegalArgumentException("discoveryRequest must not be null");
            }
            request.getDiscoveryRequest().setNamespaceId(NamespaceUtil.processNamespaceParameter(
                request.getDiscoveryRequest().getNamespaceId()));
            return watchService.subscribe(meta.getConnectionId(), request);
        } catch (Exception e) {
            AgentSubscribeRpcResponse response = new AgentSubscribeRpcResponse();
            AgentGrpcResponseErrorMapper.apply(response, e);
            return response;
        }
    }
    
    private void requireWatchAbility(RequestMeta meta) throws NacosException {
        if (meta.getConnectionAbility(AbilityKey.SDK_RAD_WATCH_V1) != AbilityStatus.SUPPORTED) {
            throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                "The client does not support the RAD Watch hint binding.");
        }
    }
}
