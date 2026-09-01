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

package com.alibaba.nacos.ai.remote.handler;

import com.alibaba.nacos.ai.service.mcp.McpClientApplicationService;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.McpServerRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

/**
 * gRPC binding for MCP Runtime Endpoint publication.
 *
 * @author Nacos
 */
@Since("3.0.3")
@Component
public class McpServerEndpointRequestHandler
    extends RequestHandler<McpServerEndpointRequest, McpServerEndpointResponse> {
    
    private final McpClientApplicationService applicationService;
    
    public McpServerEndpointRequestHandler(McpClientApplicationService applicationService) {
        this.applicationService = applicationService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = McpServerRequestParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public McpServerEndpointResponse handle(McpServerEndpointRequest request, RequestMeta meta) {
        McpRequestUtil.fillNamespaceId(request);
        McpServerEndpointResponse response = new McpServerEndpointResponse();
        try {
            applicationService.operateEndpoint(request, meta.getConnectionId(),
                NamingRequestUtil.getSourceIpForGrpcRequest(meta));
            response.setType(request.getType());
        } catch (NacosException e) {
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
        }
        return response;
    }
}
