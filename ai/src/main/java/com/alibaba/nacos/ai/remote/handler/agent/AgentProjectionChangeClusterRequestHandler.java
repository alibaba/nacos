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

import com.alibaba.nacos.ai.service.agent.watch.AgentProjectionService;
import com.alibaba.nacos.api.ai.remote.request.cluster.AgentProjectionChangeClusterRequest;
import com.alibaba.nacos.api.ai.remote.response.cluster.AgentProjectionChangeClusterResponse;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.grpc.InvokeSource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

/**
 * Invalidates active Agent Projections after a peer server commits a definition change.
 *
 * @author Nacos
 */
@Since("3.4.0")
@Component
@InvokeSource(source = {RemoteConstants.LABEL_SOURCE_CLUSTER})
public class AgentProjectionChangeClusterRequestHandler extends
    RequestHandler<AgentProjectionChangeClusterRequest, AgentProjectionChangeClusterResponse> {
    
    private final AgentProjectionService projectionService;
    
    public AgentProjectionChangeClusterRequestHandler(
        AgentProjectionService projectionService) {
        this.projectionService = projectionService;
    }
    
    @Override
    @Secured(signType = SignType.AI, apiType = ApiType.INNER_API)
    public AgentProjectionChangeClusterResponse handle(
        AgentProjectionChangeClusterRequest request, RequestMeta meta) {
        AgentValidationUtils.validateNamespaceId(request.getNamespaceId());
        AgentValidationUtils.validateAgentName(request.getAgentName());
        projectionService.onAgentChanged(request.getNamespaceId(), request.getAgentName());
        return new AgentProjectionChangeClusterResponse();
    }
}
