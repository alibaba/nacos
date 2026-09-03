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
import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityMode;
import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityModeResolver;
import com.alibaba.nacos.ai.service.a2a.CanonicalA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.a2a.LegacyA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationEndpointRouter;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationState;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.AgentRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Register or Deregister endpoint for agent to nacos AI module request handler.
 *
 * @author xiweng.yy
 */
@Since("3.1.0")
@Component
public class AgentEndpointRequestHandler
    extends RequestHandler<AgentEndpointRequest, AgentEndpointResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentEndpointRequestHandler.class);
    
    private final LegacyA2aEndpointOperationService legacyEndpointOperationService;
    
    private final A2aCompatibilityModeResolver compatibilityModeResolver;
    
    private final CanonicalA2aEndpointOperationService canonicalEndpointOperationService;
    
    private final A2aMigrationEndpointRouter migrationEndpointRouter;
    
    public AgentEndpointRequestHandler(
        LegacyA2aEndpointOperationService legacyEndpointOperationService,
        A2aCompatibilityModeResolver compatibilityModeResolver,
        CanonicalA2aEndpointOperationService canonicalEndpointOperationService,
        A2aMigrationEndpointRouter migrationEndpointRouter) {
        this.legacyEndpointOperationService = legacyEndpointOperationService;
        this.compatibilityModeResolver = compatibilityModeResolver;
        this.canonicalEndpointOperationService = canonicalEndpointOperationService;
        this.migrationEndpointRouter = migrationEndpointRouter;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentRequestParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public AgentEndpointResponse handle(AgentEndpointRequest request, RequestMeta meta)
        throws NacosException {
        AgentEndpointResponse response = new AgentEndpointResponse();
        response.setType(request.getType());
        AgentRequestUtil.fillNamespaceId(request);
        try {
            validateRequest(request);
            // TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.
            // Runtime dual materialization is isolated before the long-lived static branches.
            A2aMigrationState migrationState = migrationEndpointRouter.resolveState();
            if (migrationState != null) {
                handleMigration(request, meta, migrationState);
                return response;
            }
            if (A2aCompatibilityMode.CANONICAL == compatibilityModeResolver.resolve()) {
                handleCanonical(request, meta);
                return response;
            }
            handleLegacy(request, meta);
        } catch (NacosApiException e) {
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
            LOGGER.error("[{}] Register agent endpoint to agent {} error: {}",
                meta.getConnectionId(),
                request.getAgentName(), e.getErrMsg());
        }
        return response;
    }
    
    private void handleMigration(AgentEndpointRequest request, RequestMeta meta,
        A2aMigrationState migrationState) throws NacosException {
        String sourceIp = NamingRequestUtil.getSourceIpForGrpcRequest(meta);
        switch (request.getType()) {
            case AiRemoteConstants.REGISTER_ENDPOINT:
                migrationEndpointRouter.register(meta.getConnectionId(),
                    request.getNamespaceId(), request.getAgentName(), request.getEndpoint(),
                    sourceIp, migrationState);
                break;
            case AiRemoteConstants.DE_REGISTER_ENDPOINT:
                migrationEndpointRouter.deregister(meta.getConnectionId(),
                    request.getNamespaceId(), request.getAgentName(), request.getEndpoint(),
                    sourceIp, migrationState);
                break;
            default:
                throw invalidType(request.getType());
        }
    }
    
    private void handleLegacy(AgentEndpointRequest request, RequestMeta meta)
        throws NacosException {
        String sourceIp = NamingRequestUtil.getSourceIpForGrpcRequest(meta);
        switch (request.getType()) {
            case AiRemoteConstants.REGISTER_ENDPOINT:
                legacyEndpointOperationService.register(meta.getConnectionId(),
                    request.getNamespaceId(), request.getAgentName(), request.getEndpoint(),
                    sourceIp);
                break;
            case AiRemoteConstants.DE_REGISTER_ENDPOINT:
                legacyEndpointOperationService.deregister(meta.getConnectionId(),
                    request.getNamespaceId(), request.getAgentName(), request.getEndpoint(),
                    sourceIp);
                break;
            default:
                throw invalidType(request.getType());
        }
    }
    
    private void handleCanonical(AgentEndpointRequest request, RequestMeta meta)
        throws NacosException {
        switch (request.getType()) {
            case AiRemoteConstants.REGISTER_ENDPOINT:
                canonicalEndpointOperationService.register(meta.getConnectionId(),
                    request.getNamespaceId(), request.getAgentName(),
                    Collections.singletonList(request.getEndpoint()));
                break;
            case AiRemoteConstants.DE_REGISTER_ENDPOINT:
                canonicalEndpointOperationService.deregister(meta.getConnectionId(),
                    request.getNamespaceId(), request.getAgentName(),
                    request.getEndpoint().getVersion());
                break;
            default:
                throw invalidType(request.getType());
        }
    }
    
    private NacosApiException invalidType(String type) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            String.format("parameter `type` should be %s or %s, but was %s",
                AiRemoteConstants.REGISTER_ENDPOINT, AiRemoteConstants.DE_REGISTER_ENDPOINT,
                type));
    }
    
    private void validateRequest(AgentEndpointRequest request) throws NacosApiException {
        if (StringUtils.isBlank(request.getAgentName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `agentName` can't be empty or null");
        }
        if (null == request.getEndpoint()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `endpoint` can't be null");
        }
        if (StringUtils.isBlank(request.getEndpoint().getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `endpoint.version` can't be empty or null");
        }
    }
    
}
