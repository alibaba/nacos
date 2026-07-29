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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.AgentHandler;
import com.alibaba.nacos.console.model.ai.ConsoleRuntimeEndpointView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentProxy}.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class AgentProxyTest {
    
    private static final String NAMESPACE_ID = "test_namespace";
    
    private static final String AGENT_NAME = "Nacos Agent";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AgentHandler agentHandler;
    
    private AgentProxy agentProxy;
    
    @BeforeEach
    void setUp() {
        agentProxy = new AgentProxy(agentHandler);
    }
    
    @Test
    void shouldDelegateEveryOperationAndBuildRuntimeView() throws Exception {
        AgentOverview overview = new AgentOverview();
        Agent agent = new Agent();
        AgentUpdateRequest updateRequest = new AgentUpdateRequest();
        AgentDraftCreateRequest createRequest = new AgentDraftCreateRequest();
        AgentDraftUpdateRequest draftUpdateRequest = new AgentDraftUpdateRequest();
        AgentLabelsUpdateRequest labelsRequest = new AgentLabelsUpdateRequest();
        Page<AgentSummary> agentPage = new Page<>();
        Page<AgentVersionSummary> versionPage = new Page<>();
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        AgentVersionSummary versionSummary = new AgentVersionSummary();
        RuntimeEndpointSnapshot snapshot = new RuntimeEndpointSnapshot();
        when(agentHandler.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(overview);
        when(agentHandler.updateAgent(NAMESPACE_ID, updateRequest)).thenReturn(agent);
        when(agentHandler.listAgents(NAMESPACE_ID, AGENT_NAME, "tag", "PRIVATE", "owner",
            "download_count", 1, 10)).thenReturn(agentPage);
        when(agentHandler.listVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10))
            .thenReturn(versionPage);
        when(agentHandler.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail);
        when(agentHandler.getRuntimeEndpoints(NAMESPACE_ID, AGENT_NAME, "a2a", VERSION))
            .thenReturn(snapshot);
        when(agentHandler.createDraft(NAMESPACE_ID, createRequest)).thenReturn(versionDetail);
        when(agentHandler.updateDraft(NAMESPACE_ID, draftUpdateRequest)).thenReturn(versionDetail);
        when(agentHandler.submit(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentHandler.publish(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentHandler.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentHandler.redraft(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentHandler.online(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentHandler.offline(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentHandler.updateLabels(NAMESPACE_ID, labelsRequest)).thenReturn(agent);
        
        assertSame(overview, agentProxy.getAgent(NAMESPACE_ID, AGENT_NAME));
        assertSame(agent, agentProxy.updateAgent(NAMESPACE_ID, updateRequest));
        agentProxy.deleteAgent(NAMESPACE_ID, AGENT_NAME);
        assertSame(agentPage, agentProxy.listAgents(NAMESPACE_ID, AGENT_NAME, "tag", "PRIVATE",
            "owner", "download_count", 1, 10));
        assertSame(versionPage,
            agentProxy.listVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10));
        assertSame(versionDetail, agentProxy.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        ConsoleRuntimeEndpointView runtimeView =
            agentProxy.getRuntimeEndpoints(NAMESPACE_ID, AGENT_NAME, "a2a", VERSION);
        assertSame(snapshot, runtimeView.getRuntimeEndpointSnapshot());
        assertEquals(NAMESPACE_ID, runtimeView.getNamingServiceRef().getNamespaceId());
        assertEquals("agent-endpoints", runtimeView.getNamingServiceRef().getGroupName());
        assertEquals("rad-enc-Nacos-032Agent-a2a",
            runtimeView.getNamingServiceRef().getServiceName());
        assertSame(versionDetail, agentProxy.createDraft(NAMESPACE_ID, createRequest));
        assertSame(versionDetail, agentProxy.updateDraft(NAMESPACE_ID, draftUpdateRequest));
        agentProxy.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        assertSame(versionSummary, agentProxy.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, agentProxy.publish(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, agentProxy.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, agentProxy.redraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, agentProxy.online(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, agentProxy.offline(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(agent, agentProxy.updateLabels(NAMESPACE_ID, labelsRequest));
        
        verify(agentHandler).deleteAgent(NAMESPACE_ID, AGENT_NAME);
        verify(agentHandler).deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
    }
}
