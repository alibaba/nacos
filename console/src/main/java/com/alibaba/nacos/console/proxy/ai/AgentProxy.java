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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
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
import com.alibaba.nacos.console.model.ai.ConsoleRuntimeEndpointView;
import com.alibaba.nacos.console.model.ai.ConsoleRuntimeEndpointView.NamingServiceRef;
import org.springframework.stereotype.Component;

/**
 * Console workflow proxy for protocol-neutral Agent management.
 *
 * @author Nacos
 */
@Component
public class AgentProxy {
    
    private final AgentHandler agentHandler;
    
    public AgentProxy(AgentHandler agentHandler) {
        this.agentHandler = agentHandler;
    }
    
    public AgentOverview getAgent(String namespaceId, String agentName) throws NacosException {
        return agentHandler.getAgent(namespaceId, agentName);
    }
    
    public Agent updateAgent(String namespaceId, AgentUpdateRequest request)
        throws NacosException {
        return agentHandler.updateAgent(namespaceId, request);
    }
    
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        agentHandler.deleteAgent(namespaceId, agentName);
    }
    
    public Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        return agentHandler.listAgents(namespaceId, agentName, bizTag, scope, owner, orderBy,
            pageNo, pageSize);
    }
    
    public Page<AgentVersionSummary> listVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        return agentHandler.listVersions(namespaceId, agentName, status, pageNo, pageSize);
    }
    
    public AgentVersionDetail getVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.getVersion(namespaceId, agentName, version);
    }
    
    public ConsoleRuntimeEndpointView getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        RuntimeEndpointSnapshot snapshot =
            agentHandler.getRuntimeEndpoints(namespaceId, agentName, protocol, version);
        NamingServiceRef serviceRef = new NamingServiceRef(namespaceId,
            Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose(agentName, protocol));
        return new ConsoleRuntimeEndpointView(snapshot, serviceRef);
    }
    
    public AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException {
        return agentHandler.createDraft(namespaceId, request);
    }
    
    public AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException {
        return agentHandler.updateDraft(namespaceId, request);
    }
    
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        agentHandler.deleteDraft(namespaceId, agentName, version);
    }
    
    public AgentVersionSummary submit(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.submit(namespaceId, agentName, version);
    }
    
    public AgentVersionSummary publish(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.publish(namespaceId, agentName, version);
    }
    
    public AgentVersionSummary forcePublish(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.forcePublish(namespaceId, agentName, version);
    }
    
    public AgentVersionSummary redraft(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.redraft(namespaceId, agentName, version);
    }
    
    public AgentVersionSummary online(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.online(namespaceId, agentName, version);
    }
    
    public AgentVersionSummary offline(String namespaceId, String agentName, String version)
        throws NacosException {
        return agentHandler.offline(namespaceId, agentName, version);
    }
    
    public Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException {
        return agentHandler.updateLabels(namespaceId, request);
    }
}
