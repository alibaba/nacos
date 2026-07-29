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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.AgentHandler;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import org.springframework.stereotype.Service;

/**
 * Console-only deployment handler for protocol-neutral Agent management.
 *
 * @author Nacos
 */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class AgentRemoteHandler implements AgentHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public AgentRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public AgentOverview getAgent(String namespaceId, String agentName) throws NacosException {
        return agentService().getAgent(namespaceId, agentName);
    }
    
    @Override
    public Agent updateAgent(String namespaceId, AgentUpdateRequest request)
        throws NacosException {
        return agentService().updateAgent(namespaceId, request);
    }
    
    @Override
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        agentService().deleteAgent(namespaceId, agentName);
    }
    
    @Override
    public Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        return agentService().listAgents(namespaceId, agentName, bizTag, scope, owner, orderBy,
            pageNo, pageSize);
    }
    
    @Override
    public Page<AgentVersionSummary> listVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        return agentService().listAgentVersions(namespaceId, agentName, status, pageNo, pageSize);
    }
    
    @Override
    public AgentVersionDetail getVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().getAgentVersion(namespaceId, agentName, version);
    }
    
    @Override
    public RuntimeEndpointSnapshot getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        return agentService().getRuntimeEndpoints(namespaceId, agentName, protocol, version);
    }
    
    @Override
    public AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException {
        return agentService().createDraft(namespaceId, request);
    }
    
    @Override
    public AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException {
        return agentService().updateDraft(namespaceId, request);
    }
    
    @Override
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        agentService().deleteDraft(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary submit(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().submit(namespaceId, versionCommand(agentName, version));
    }
    
    @Override
    public AgentVersionSummary publish(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().publish(namespaceId, versionCommand(agentName, version));
    }
    
    @Override
    public AgentVersionSummary forcePublish(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().forcePublish(namespaceId, versionCommand(agentName, version));
    }
    
    @Override
    public AgentVersionSummary redraft(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().redraft(namespaceId, versionCommand(agentName, version));
    }
    
    @Override
    public AgentVersionSummary online(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().online(namespaceId, versionCommand(agentName, version));
    }
    
    @Override
    public AgentVersionSummary offline(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentService().offline(namespaceId, versionCommand(agentName, version));
    }
    
    @Override
    public Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException {
        return agentService().updateLabels(namespaceId, request);
    }
    
    private AgentMaintainerService agentService() {
        return clientHolder.getAiMaintainerService().agent();
    }
    
    private AgentVersionCommand versionCommand(String agentName, String version) {
        AgentVersionCommand result = new AgentVersionCommand();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }
}
