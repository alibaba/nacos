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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.AgentHandler;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Component;

/**
 * Merged-deployment handler for protocol-neutral Agent management.
 *
 * @author Nacos
 */
@Component
@EnabledInnerHandler
@EnabledAiHandler
public class AgentInnerHandler implements AgentHandler {
    
    private final AgentOperationService agentOperationService;
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    public AgentInnerHandler(AgentOperationService agentOperationService,
        AgentRuntimeRegistryService runtimeRegistryService) {
        this.agentOperationService = agentOperationService;
        this.runtimeRegistryService = runtimeRegistryService;
    }
    
    @Override
    public AgentOverview getAgent(String namespaceId, String agentName) throws NacosException {
        return agentOperationService.getOverview(namespaceId, agentName);
    }
    
    @Override
    public Agent updateAgent(String namespaceId, AgentUpdateRequest request)
        throws NacosException {
        return agentOperationService.updateAgent(toAgent(namespaceId, request));
    }
    
    @Override
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        agentOperationService.deleteAgent(namespaceId, agentName);
    }
    
    @Override
    public Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        return agentOperationService.listAgents(namespaceId, agentName, bizTag, scope, owner,
            orderBy, pageNo, pageSize);
    }
    
    @Override
    public Page<AgentVersionSummary> listVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        return agentOperationService.listVersions(namespaceId, agentName, status, pageNo,
            pageSize);
    }
    
    @Override
    public AgentVersionDetail getVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.getVersion(namespaceId, agentName, version);
    }
    
    @Override
    public RuntimeEndpointSnapshot getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        return runtimeRegistryService.getRuntimeEndpointSnapshot(namespaceId, agentName, protocol,
            version);
    }
    
    @Override
    public AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException {
        return agentOperationService.createDraft(namespaceId, request);
    }
    
    @Override
    public AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException {
        return agentOperationService.updateDraft(namespaceId, request.getAgentName(),
            request.getVersion(), request.getCallInterfaces(), request.getChangeDescription());
    }
    
    @Override
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        agentOperationService.deleteDraft(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary submit(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.submit(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary publish(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.publish(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary forcePublish(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.forcePublish(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary redraft(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.redraft(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary online(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.online(namespaceId, agentName, version);
    }
    
    @Override
    public AgentVersionSummary offline(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentOperationService.offline(namespaceId, agentName, version);
    }
    
    @Override
    public Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException {
        return agentOperationService.updateLabels(namespaceId, request.getAgentName(),
            request.getLabels());
    }
    
    private Agent toAgent(String namespaceId, AgentUpdateRequest request) {
        Agent result = new Agent();
        result.setNamespaceId(namespaceId);
        result.setAgentName(request.getAgentName());
        result.setDisplayName(request.getDisplayName());
        result.setDescription(request.getDescription());
        result.setIconUrl(request.getIconUrl());
        result.setProvider(request.getProvider());
        result.setTags(request.getTags());
        result.setExtensions(request.getExtensions());
        result.setStatus(request.getStatus());
        return result;
    }
}
