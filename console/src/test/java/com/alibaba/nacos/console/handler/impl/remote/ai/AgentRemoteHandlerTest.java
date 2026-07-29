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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentRemoteHandler}.
 *
 * @author Nacos
 */
class AgentRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    private static final String NAMESPACE_ID = "test_namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AgentMaintainerService agentMaintainerService;
    
    private AgentRemoteHandler handler;
    
    @BeforeEach
    void setUp() {
        setUpWithAi();
        when(aiMaintainerService.agent()).thenReturn(agentMaintainerService);
        handler = new AgentRemoteHandler(clientHolder);
    }
    
    @Test
    void shouldDelegateEveryOperationToMaintainerService() throws Exception {
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
        when(agentMaintainerService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(overview);
        when(agentMaintainerService.updateAgent(NAMESPACE_ID, updateRequest)).thenReturn(agent);
        when(agentMaintainerService.listAgents(NAMESPACE_ID, AGENT_NAME, "tag", "PRIVATE",
            "owner", "download_count", 1, 10)).thenReturn(agentPage);
        when(agentMaintainerService.listAgentVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10))
            .thenReturn(versionPage);
        when(agentMaintainerService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail);
        when(agentMaintainerService.getRuntimeEndpoints(NAMESPACE_ID, AGENT_NAME, "a2a", VERSION))
            .thenReturn(snapshot);
        when(agentMaintainerService.createDraft(NAMESPACE_ID, createRequest))
            .thenReturn(versionDetail);
        when(agentMaintainerService.updateDraft(NAMESPACE_ID, draftUpdateRequest))
            .thenReturn(versionDetail);
        when(agentMaintainerService.submit(any(), any())).thenReturn(versionSummary);
        when(agentMaintainerService.publish(any(), any())).thenReturn(versionSummary);
        when(agentMaintainerService.forcePublish(any(), any())).thenReturn(versionSummary);
        when(agentMaintainerService.redraft(any(), any())).thenReturn(versionSummary);
        when(agentMaintainerService.online(any(), any())).thenReturn(versionSummary);
        when(agentMaintainerService.offline(any(), any())).thenReturn(versionSummary);
        when(agentMaintainerService.updateLabels(NAMESPACE_ID, labelsRequest)).thenReturn(agent);
        
        assertSame(overview, handler.getAgent(NAMESPACE_ID, AGENT_NAME));
        assertSame(agent, handler.updateAgent(NAMESPACE_ID, updateRequest));
        handler.deleteAgent(NAMESPACE_ID, AGENT_NAME);
        assertSame(agentPage, handler.listAgents(NAMESPACE_ID, AGENT_NAME, "tag", "PRIVATE",
            "owner", "download_count", 1, 10));
        assertSame(versionPage,
            handler.listVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10));
        assertSame(versionDetail, handler.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(snapshot,
            handler.getRuntimeEndpoints(NAMESPACE_ID, AGENT_NAME, "a2a", VERSION));
        assertSame(versionDetail, handler.createDraft(NAMESPACE_ID, createRequest));
        assertSame(versionDetail, handler.updateDraft(NAMESPACE_ID, draftUpdateRequest));
        handler.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        assertSame(versionSummary, handler.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, handler.publish(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, handler.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, handler.redraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, handler.online(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(versionSummary, handler.offline(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(agent, handler.updateLabels(NAMESPACE_ID, labelsRequest));
        
        verify(agentMaintainerService).deleteAgent(NAMESPACE_ID, AGENT_NAME);
        verify(agentMaintainerService).deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        ArgumentCaptor<AgentVersionCommand> commandCaptor =
            ArgumentCaptor.forClass(AgentVersionCommand.class);
        verify(agentMaintainerService).submit(
            org.mockito.ArgumentMatchers.eq(NAMESPACE_ID), commandCaptor.capture());
        assertEquals(AGENT_NAME, commandCaptor.getValue().getAgentName());
        assertEquals(VERSION, commandCaptor.getValue().getVersion());
    }
}
