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
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.stereotype.Service;

/**
 * Applies Client publication semantics without retrying uncertain writes.
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
     * Create or replace a draft, optionally submitting through the ordinary Pipeline.
     *
     * @param namespaceId effective namespace
     * @param request publication request
     * @return resulting exact Version; existing non-drafts are unchanged
     * @throws NacosException when validation, persistence or submit fails
     */
    public AgentVersionDetail publish(String namespaceId, AgentPublishRequest request)
        throws NacosException {
        if (request == null) {
            throw new IllegalArgumentException("Agent publish request must not be null");
        }
        AgentValidationUtils.validateNamespaceId(namespaceId);
        request.validate();
        if (request.getCallInterfaces() != null) {
            AgentVersionContentSerializer
                .serialize(new AgentVersionContent(request.getCallInterfaces()));
        }
        AgentOperationService.PublicationResult prepared =
            operationService.writeDraftFromPublication(namespaceId, request);
        AgentVersionDetail current = prepared.getVersion();
        if (!AiConstants.Agent.VERSION_STATUS_DRAFT.equals(current.getStatus())
            || !prepared.isFirstVersion() && !request.isAutoSubmit()) {
            return current;
        }
        operationService.submit(namespaceId, request.getAgentName(), request.getVersion());
        return operationService.getVersion(namespaceId, request.getAgentName(),
            request.getVersion());
    }
}
