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
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentInnerHandler}.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class AgentInnerHandlerTest {
    
    private static final String NAMESPACE_ID = "test_namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AgentOperationService agentOperationService;
    
    @Mock
    private AgentRuntimeRegistryService runtimeRegistryService;
    
    private AgentInnerHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentInnerHandler(agentOperationService, runtimeRegistryService);
    }
    
    @Test
    void shouldDelegateEveryOperationToLocalServices() throws Exception {
        AgentOverview overview = new AgentOverview();
        Agent persistedAgent = new Agent();
        AgentDraftUpdateRequest draftUpdateRequest = new AgentDraftUpdateRequest();
        List<AgentCallInterface> callInterfaces =
            Collections.singletonList(new AgentCallInterface());
        draftUpdateRequest.setAgentName(AGENT_NAME);
        draftUpdateRequest.setVersion(VERSION);
        draftUpdateRequest.setCallInterfaces(callInterfaces);
        draftUpdateRequest.setChangeDescription("change");
        AgentLabelsUpdateRequest labelsRequest = new AgentLabelsUpdateRequest();
        labelsRequest.setAgentName(AGENT_NAME);
        labelsRequest.setLabels(Collections.singletonMap("stable", VERSION));
        Page<AgentSummary> agentPage = new Page<>();
        Page<AgentVersionSummary> versionPage = new Page<>();
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        AgentVersionSummary versionSummary = new AgentVersionSummary();
        RuntimeEndpointSnapshot snapshot = new RuntimeEndpointSnapshot();
        when(agentOperationService.getOverview(NAMESPACE_ID, AGENT_NAME)).thenReturn(overview);
        when(agentOperationService.updateAgent(org.mockito.ArgumentMatchers.any(Agent.class)))
            .thenReturn(persistedAgent);
        when(agentOperationService.listAgents(NAMESPACE_ID, AGENT_NAME, "tag", "PRIVATE", "owner",
            "download_count", 1, 10)).thenReturn(agentPage);
        when(agentOperationService.listVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10))
            .thenReturn(versionPage);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail);
        when(runtimeRegistryService.getRuntimeEndpointSnapshot(NAMESPACE_ID, AGENT_NAME, "a2a",
            VERSION)).thenReturn(snapshot);
        AgentDraftCreateRequest createRequest = new AgentDraftCreateRequest();
        when(agentOperationService.createDraft(NAMESPACE_ID, createRequest))
            .thenReturn(versionDetail);
        when(agentOperationService.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION, callInterfaces,
            "change")).thenReturn(versionDetail);
        when(agentOperationService.submit(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentOperationService.publish(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentOperationService.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentOperationService.redraft(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentOperationService.online(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentOperationService.offline(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentOperationService.updateLabels(NAMESPACE_ID, AGENT_NAME,
            labelsRequest.getLabels())).thenReturn(persistedAgent);
        
        assertSame(overview, handler.getAgent(NAMESPACE_ID, AGENT_NAME));
        AgentUpdateRequest updateRequest = updateRequest();
        assertSame(persistedAgent, handler.updateAgent(NAMESPACE_ID, updateRequest));
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
        assertSame(persistedAgent, handler.updateLabels(NAMESPACE_ID, labelsRequest));
        
        ArgumentCaptor<Agent> agentCaptor = ArgumentCaptor.forClass(Agent.class);
        verify(agentOperationService).updateAgent(agentCaptor.capture());
        assertMappedAgent(agentCaptor.getValue(), updateRequest);
        verify(agentOperationService).deleteAgent(NAMESPACE_ID, AGENT_NAME);
        verify(agentOperationService).deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
    }
    
    private AgentUpdateRequest updateRequest() {
        AgentUpdateRequest result = new AgentUpdateRequest();
        result.setAgentName(AGENT_NAME);
        result.setDisplayName("display");
        result.setDescription("description");
        result.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("provider");
        result.setProvider(provider);
        result.setTags(List.of("tag"));
        result.setExtensions(Map.of("key", "value"));
        result.setStatus("enable");
        return result;
    }
    
    private void assertMappedAgent(Agent actual, AgentUpdateRequest expected) {
        assertEquals(NAMESPACE_ID, actual.getNamespaceId());
        assertEquals(expected.getAgentName(), actual.getAgentName());
        assertEquals(expected.getDisplayName(), actual.getDisplayName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getIconUrl(), actual.getIconUrl());
        assertSame(expected.getProvider(), actual.getProvider());
        assertSame(expected.getTags(), actual.getTags());
        assertSame(expected.getExtensions(), actual.getExtensions());
        assertEquals(expected.getStatus(), actual.getStatus());
    }
}
