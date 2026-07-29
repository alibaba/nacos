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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.AgentHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Disabled-function handler for protocol-neutral Agent management.
 *
 * @author Nacos
 */
@Component
@ConditionalOnMissingBean(value = AgentHandler.class, ignored = AgentNoopHandler.class)
public class AgentNoopHandler implements AgentHandler {
    
    private static final String AGENT_NOT_ENABLED_MESSAGE =
        "Nacos AI Agent module and API required both `naming` and `config` module.";
    
    @Override
    public AgentOverview getAgent(String namespaceId, String agentName) throws NacosException {
        throw disabled();
    }
    
    @Override
    public Agent updateAgent(String namespaceId, AgentUpdateRequest request)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        throw disabled();
    }
    
    @Override
    public Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public Page<AgentVersionSummary> listVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionDetail getVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public RuntimeEndpointSnapshot getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionSummary submit(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionSummary publish(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionSummary forcePublish(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionSummary redraft(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionSummary online(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public AgentVersionSummary offline(String namespaceId, String agentName, String version)
        throws NacosException {
        throw disabled();
    }
    
    @Override
    public Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException {
        throw disabled();
    }
    
    private NacosApiException disabled() {
        return new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, AGENT_NOT_ENABLED_MESSAGE);
    }
}
