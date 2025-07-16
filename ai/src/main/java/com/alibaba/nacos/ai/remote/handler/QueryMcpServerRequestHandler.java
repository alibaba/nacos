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

import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
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
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.McpServerRequestParamExtractor;
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
    
    private final McpServerIndex mcpServerIndex;
    
    public QueryMcpServerRequestHandler(McpServerOperationService mcpServerOperationService,
            McpServerIndex mcpServerIndex) {
        this.mcpServerOperationService = mcpServerOperationService;
        this.mcpServerIndex = mcpServerIndex;
    }
    
    @Override
    @ExtractorManager.Extractor(rpcExtractor = McpServerRequestParamExtractor.class)
    public QueryMcpServerResponse handle(QueryMcpServerRequest request, RequestMeta meta) throws NacosException {
        McpRequestUtils.fillNamespaceId(request);
        checkParameters(request);
        return doHandler(request, meta);
    }
    
    private void checkParameters(QueryMcpServerRequest request) throws NacosException {
        if (StringUtils.isBlank(request.getMcpName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
    }
    
    private QueryMcpServerResponse doHandler(QueryMcpServerRequest request, RequestMeta meta) throws NacosException {
        McpServerIndexData indexData = mcpServerIndex.getMcpServerByName(request.getNamespaceId(),
                request.getMcpName());
        if (null == indexData) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND,
                    String.format("MCP server `%s` not found in namespaceId: `%s`", request.getMcpName(),
                            request.getNamespaceId()));
        }
        McpServerDetailInfo detailInfo = mcpServerOperationService.getMcpServerDetail(request.getNamespaceId(),
                indexData.getId(), null, request.getVersion());
        QueryMcpServerResponse response = new QueryMcpServerResponse();
        response.setMcpServerDetailInfo(detailInfo);
        return response;
    }
}
