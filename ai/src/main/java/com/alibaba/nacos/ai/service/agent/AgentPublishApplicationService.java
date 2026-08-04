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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Orchestrates idempotent Agent draft creation and optional ordinary submit for Client APIs.
 *
 * @author Nacos
 */
@Service
public class AgentPublishApplicationService {
    
    private final AgentOperationService operationService;
    
    public AgentPublishApplicationService(AgentOperationService operationService) {
        this.operationService = operationService;
    }
    
    /**
     * Publish one exact Agent Version and optionally submit it.
     *
     * @param namespaceId effective namespace
     * @param request publication request
     * @return resulting exact Version detail
     * @throws NacosException when validation, persistence, or submit fails
     */
    public AgentVersionDetail publish(String namespaceId, AgentPublishRequest request)
        throws NacosException {
        if (request == null) {
            throw new IllegalArgumentException("Agent publish request must not be null");
        }
        AgentValidationUtils.validateNamespaceId(namespaceId);
        request.validate();
        AgentVersionDetail current = findEquivalent(namespaceId, request);
        if (current == null) {
            try {
                current = operationService.createDraft(namespaceId, request);
            } catch (NacosException createFailure) {
                current = recoverEquivalent(namespaceId, request, createFailure);
            }
        }
        if (!request.isAutoSubmit()) {
            requireStatus(current, AiConstants.Agent.VERSION_STATUS_DRAFT);
            return current;
        }
        if (!AiConstants.Agent.VERSION_STATUS_DRAFT.equals(current.getStatus())) {
            return requireSubmittedState(current);
        }
        try {
            operationService.submit(namespaceId, request.getAgentName(), request.getVersion());
        } catch (NacosException submitFailure) {
            AgentVersionDetail recovered = recoverEquivalent(namespaceId, request, submitFailure);
            if (AiConstants.Agent.VERSION_STATUS_DRAFT.equals(recovered.getStatus())) {
                throw submitFailure;
            }
            return requireSubmittedState(recovered);
        }
        return requireSubmittedState(operationService.getVersion(namespaceId,
            request.getAgentName(), request.getVersion()));
    }
    
    private AgentVersionDetail findEquivalent(String namespaceId, AgentPublishRequest request)
        throws NacosException {
        final AgentVersionDetail existing;
        try {
            existing = operationService.getVersion(namespaceId, request.getAgentName(),
                request.getVersion());
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
        requireEquivalentContent(namespaceId, request, existing);
        requireEquivalentInitialMetadata(namespaceId, request);
        return existing;
    }
    
    private AgentVersionDetail recoverEquivalent(String namespaceId, AgentPublishRequest request,
        NacosException originalFailure) throws NacosException {
        final AgentVersionDetail existing;
        try {
            existing = operationService.getVersion(namespaceId, request.getAgentName(),
                request.getVersion());
        } catch (NacosException readFailure) {
            originalFailure.addSuppressed(readFailure);
            throw originalFailure;
        }
        requireEquivalentContent(namespaceId, request, existing);
        requireEquivalentInitialMetadata(namespaceId, request);
        return existing;
    }
    
    private void requireEquivalentContent(String namespaceId, AgentPublishRequest request,
        AgentVersionDetail existing) throws NacosException {
        String requestedDigest;
        if (request.getCallInterfaces() != null) {
            requestedDigest = AgentVersionContentSerializer.serialize(
                new AgentVersionContent(request.getCallInterfaces())).getContentDigest();
        } else {
            requestedDigest = operationService.getVersion(namespaceId, request.getAgentName(),
                request.getBasedOnVersion()).getContentDigest();
        }
        if (!Objects.equals(requestedDigest, existing.getContentDigest())
            || !Objects.equals(request.getAuthor(), existing.getAuthor())
            || !Objects.equals(request.getChangeDescription(), existing.getChangeDescription())) {
            throw conflict("Agent Version already exists with different content: "
                + request.getAgentName() + '@' + request.getVersion());
        }
    }
    
    private void requireEquivalentInitialMetadata(String namespaceId,
        AgentPublishRequest request) throws NacosException {
        if (!hasInitialMetadata(request)) {
            return;
        }
        Agent existing = operationService.getAgent(namespaceId, request.getAgentName());
        if (request.getDisplayName() != null
            && !Objects.equals(request.getDisplayName(), existing.getDisplayName())
            || request.getDescription() != null
                && !Objects.equals(request.getDescription(), existing.getDescription())
            || request.getIconUrl() != null
                && !Objects.equals(request.getIconUrl(), existing.getIconUrl())
            || request.getProvider() != null
                && !sameProvider(request.getProvider(), existing.getProvider())
            || request.getTags() != null && !Objects.equals(request.getTags(), existing.getTags())
            || request.getExtensions() != null
                && !Objects.equals(request.getExtensions(), existing.getExtensions())) {
            throw conflict("Agent already exists with different initial metadata: "
                + request.getAgentName());
        }
    }
    
    private boolean hasInitialMetadata(AgentPublishRequest request) {
        return request.getDisplayName() != null || request.getDescription() != null
            || request.getIconUrl() != null || request.getProvider() != null
            || request.getTags() != null || request.getExtensions() != null;
    }
    
    private boolean sameProvider(AgentProvider requested, AgentProvider existing) {
        return existing != null && Objects.equals(requested.getName(), existing.getName())
            && Objects.equals(requested.getUrl(), existing.getUrl());
    }
    
    private AgentVersionDetail requireSubmittedState(AgentVersionDetail version)
        throws NacosApiException {
        String status = version.getStatus();
        if (AiConstants.Agent.VERSION_STATUS_REVIEWING.equals(status)
            || AiConstants.Agent.VERSION_STATUS_REVIEWED.equals(status)
            || AiConstants.Agent.VERSION_STATUS_ONLINE.equals(status)) {
            return version;
        }
        throw illegalState("Agent Version did not reach a submitted state: "
            + version.getAgentName() + '@' + version.getVersion() + ", status=" + status);
    }
    
    private void requireStatus(AgentVersionDetail version, String expected)
        throws NacosApiException {
        if (!expected.equals(version.getStatus())) {
            throw illegalState("Agent Version status must be " + expected + ": "
                + version.getAgentName() + '@' + version.getVersion());
        }
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            message);
    }
    
    private NacosApiException illegalState(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.ILLEGAL_STATE,
            message);
    }
}
