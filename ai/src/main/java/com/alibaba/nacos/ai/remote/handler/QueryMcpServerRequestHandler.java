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

package com.alibaba.nacos.ai.remote.handler;

import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.utils.McpRequestUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import org.springframework.stereotype.Component;

/**
 * Nacos AI module query mcp request handler.
 *
 * @author xiweng.yy
 */
@Component
public class QueryMcpServerRequestHandler extends RequestHandler<QueryMcpServerRequest, QueryMcpServerResponse> {
    
    private final McpServerOperationService mcpServerOperationService;
    
    public QueryMcpServerRequestHandler(McpServerOperationService mcpServerOperationService) {
        this.mcpServerOperationService = mcpServerOperationService;
    }
    
    @Override
    public QueryMcpServerResponse handle(QueryMcpServerRequest request, RequestMeta meta) throws NacosException {
        McpRequestUtils.fillNamespaceId(request);
        checkParameters(request);
        return doHandler(request, meta);
    }
    
    /**
     * TODO, abstract to parameter check filter {@link com.alibaba.nacos.core.remote.grpc.RemoteParamCheckFilter}.
     */
    private void checkParameters(QueryMcpServerRequest request) throws NacosException {
        if (StringUtils.isBlank(request.getMcpId())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpId` can't be empty or null");
        }
    }
    
    private QueryMcpServerResponse doHandler(QueryMcpServerRequest request, RequestMeta meta) throws NacosException {
        McpServerDetailInfo detailInfo = mcpServerOperationService.getMcpServerDetail(request.getNamespaceId(),
                request.getMcpId(), null, request.getVersion());
        QueryMcpServerResponse response = new QueryMcpServerResponse();
        response.setMcpServerDetailInfo(detailInfo);
        return response;
    }
}
