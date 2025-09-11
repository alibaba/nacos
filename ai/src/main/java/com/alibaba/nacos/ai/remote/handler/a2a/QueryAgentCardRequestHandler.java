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

package com.alibaba.nacos.ai.remote.handler.a2a;

import com.alibaba.nacos.ai.service.a2a.A2aServerOperationService;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryAgentCardResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.AgentRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nacos AI module query agent card request handler.
 *
 * @author xiweng.yy
 */
@Component
public class QueryAgentCardRequestHandler extends RequestHandler<QueryAgentCardRequest, QueryAgentCardResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryAgentCardRequestHandler.class);
    
    private final A2aServerOperationService a2aServerOperationService;
    
    public QueryAgentCardRequestHandler(A2aServerOperationService a2aServerOperationService) {
        this.a2aServerOperationService = a2aServerOperationService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentRequestParamExtractor.class)
    @Secured(action = ActionTypes.READ, signType = SignType.AI)
    public QueryAgentCardResponse handle(QueryAgentCardRequest request, RequestMeta meta) throws NacosException {
        AgentRequestUtil.fillNamespaceId(request);
        if (StringUtils.isBlank(request.getAgentName())) {
            QueryAgentCardResponse errorResponse = new QueryAgentCardResponse();
            errorResponse.setErrorInfo(NacosException.INVALID_PARAM, "parameters `agentName` can't be empty or null");
            return errorResponse;
        }
        return doHandler(request);
    }
    
    private QueryAgentCardResponse doHandler(QueryAgentCardRequest request) {
        QueryAgentCardResponse response = new QueryAgentCardResponse();
        try {
            AgentCardDetailInfo result = a2aServerOperationService.getAgentCard(request.getNamespaceId(),
                    request.getAgentName(), request.getVersion(), request.getRegistrationType());
            response.setAgentCardDetailInfo(result);
        } catch (NacosException e) {
            LOGGER.error("Query agent card for agent {} error: {}", request.getAgentName(), e.getErrMsg());
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
        }
        return response;
    }
}
