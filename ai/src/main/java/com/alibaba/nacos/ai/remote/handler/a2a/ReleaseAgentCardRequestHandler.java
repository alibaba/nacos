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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityOperationService;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.response.ReleaseAgentCardResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
 * Nacos AI module release agent card request handler.
 *
 * @author xiweng.yy
 */
@Since("3.1.0")
@Component
public class ReleaseAgentCardRequestHandler
    extends RequestHandler<ReleaseAgentCardRequest, ReleaseAgentCardResponse> {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ReleaseAgentCardRequestHandler.class);
    
    private final A2aCompatibilityOperationService a2aServerOperationService;
    
    public ReleaseAgentCardRequestHandler(
        A2aCompatibilityOperationService a2aServerOperationService) {
        this.a2aServerOperationService = a2aServerOperationService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentRequestParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public ReleaseAgentCardResponse handle(ReleaseAgentCardRequest request, RequestMeta meta)
        throws NacosException {
        AgentRequestUtil.fillNamespaceId(request);
        ReleaseAgentCardResponse response = new ReleaseAgentCardResponse();
        try {
            validateRequest(request);
            doHandler(request, meta);
            return response;
        } catch (NacosException e) {
            setMigrationAwareError(response, e);
            LOGGER.error("[{}] Release agent card {} error: {}", meta.getConnectionId(),
                null == request.getAgentCard() ? null : JacksonUtils.toJson(request.getAgentCard()),
                e.getErrMsg());
        }
        return response;
    }
    
    private void setMigrationAwareError(ReleaseAgentCardResponse response, NacosException error) {
        // TODO(remove in 4.0): the Nacos 3.0-3.2 migration fence must preserve its retryable
        // detail code through the historical A2A gRPC facade. Keep every other legacy error
        // mapping unchanged until this facade is removed.
        if (error instanceof NacosApiException
            && ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode() == ((NacosApiException) error)
                .getDetailErrCode()) {
            response.setErrorInfo(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(),
                error.getErrMsg());
            return;
        }
        response.setErrorInfo(error.getErrCode(), error.getErrMsg());
    }
    
    private void validateRequest(ReleaseAgentCardRequest request) throws NacosApiException {
        if (null == request.getAgentCard()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `agentCard` can't be null");
        }
        AgentRequestUtil.validateAgentCard(request.getAgentCard());
    }
    
    private void doHandler(ReleaseAgentCardRequest request, RequestMeta meta)
        throws NacosException {
        String namespaceId = request.getNamespaceId();
        AgentCard agentCard = request.getAgentCard();
        LOGGER.info("Release new agent {}, version {} into namespaceId {} from connectionId {}.",
            agentCard.getName(),
            agentCard.getVersion(), namespaceId, meta.getConnectionId());
        a2aServerOperationService.releaseAgent(agentCard, namespaceId,
            request.getRegistrationType(), request.isSetAsLatest());
        LOGGER.info("AgentCard {} version {} released.", agentCard.getName(),
            agentCard.getVersion());
    }
}
