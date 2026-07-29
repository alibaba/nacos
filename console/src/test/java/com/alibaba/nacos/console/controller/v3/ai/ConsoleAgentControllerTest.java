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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentAdminForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentDraftCreateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentDraftUpdateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentListForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentRuntimeEndpointForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentUpdateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentVersionForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentVersionListForm;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.model.ai.ConsoleRuntimeEndpointView;
import com.alibaba.nacos.console.model.ai.ConsoleRuntimeEndpointView.NamingServiceRef;
import com.alibaba.nacos.console.proxy.ai.AgentProxy;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsoleAgentController}.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class ConsoleAgentControllerTest {
    
    private static final String NAMESPACE_ID = "test_namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AgentProxy agentProxy;
    
    private ConsoleAgentController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ConsoleAgentController(agentProxy);
    }
    
    @Test
    void shouldDelegateEveryConsoleOperation() throws Exception {
        AgentOverview overview = new AgentOverview();
        Agent agent = new Agent();
        Page<AgentSummary> agentPage = new Page<>();
        Page<AgentVersionSummary> versionPage = new Page<>();
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        AgentVersionSummary versionSummary = new AgentVersionSummary();
        ConsoleRuntimeEndpointView runtimeView = new ConsoleRuntimeEndpointView(
            new RuntimeEndpointSnapshot(),
            new NamingServiceRef(NAMESPACE_ID, "agent-endpoints", "rad-test-agent-a2a"));
        when(agentProxy.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(overview);
        when(agentProxy.updateAgent(any(), any())).thenReturn(agent);
        when(agentProxy.listAgents(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(agentPage);
        when(agentProxy.listVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10))
            .thenReturn(versionPage);
        when(agentProxy.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionDetail);
        when(agentProxy.getRuntimeEndpoints(NAMESPACE_ID, AGENT_NAME, "a2a", VERSION))
            .thenReturn(runtimeView);
        when(agentProxy.createDraft(any(), any())).thenReturn(versionDetail);
        when(agentProxy.updateDraft(any(), any())).thenReturn(versionDetail);
        when(agentProxy.submit(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentProxy.publish(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentProxy.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionSummary);
        when(agentProxy.redraft(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentProxy.online(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentProxy.offline(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(versionSummary);
        when(agentProxy.updateLabels(any(), any())).thenReturn(agent);
        
        assertSame(overview, controller.getAgent(agentForm()).getData());
        assertSame(agent, controller.updateAgent(updateForm()).getData());
        assertNull(controller.deleteAgent(agentForm()).getData());
        assertSame(agentPage,
            controller.listAgents(listForm(), filterForm(), pageForm()).getData());
        assertSame(versionPage,
            controller.listVersions(versionListForm(), pageForm()).getData());
        assertSame(versionDetail, controller.getVersion(versionForm()).getData());
        assertSame(runtimeView, controller.getRuntimeEndpoints(runtimeForm()).getData());
        assertSame(versionDetail, controller.createDraft(createDraftForm()).getData());
        assertSame(versionDetail, controller.updateDraft(updateDraftForm()).getData());
        assertNull(controller.deleteDraft(versionForm()).getData());
        assertSame(versionSummary, controller.submit(versionForm()).getData());
        assertSame(versionSummary, controller.publish(versionForm()).getData());
        assertSame(versionSummary, controller.forcePublish(versionForm()).getData());
        assertSame(versionSummary, controller.redraft(versionForm()).getData());
        assertSame(versionSummary, controller.online(versionForm()).getData());
        assertSame(versionSummary, controller.offline(versionForm()).getData());
        assertSame(agent, controller.updateLabels(labelsForm()).getData());
        
        verify(agentProxy).deleteAgent(NAMESPACE_ID, AGENT_NAME);
        verify(agentProxy).listAgents(NAMESPACE_ID, AGENT_NAME, "tag", "PRIVATE", "owner",
            "download_count", 1, 10);
        verify(agentProxy).deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
    }
    
    private AgentAdminForm agentForm() {
        AgentAdminForm result = new AgentAdminForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        return result;
    }
    
    private AgentUpdateForm updateForm() {
        AgentUpdateForm result = new AgentUpdateForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setStatus("enable");
        return result;
    }
    
    private AgentListForm listForm() {
        AgentListForm result = new AgentListForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setOrderBy("download_count");
        return result;
    }
    
    private AiResourceFilterableForm filterForm() {
        AiResourceFilterableForm result = new AiResourceFilterableForm();
        result.setBizTag("tag");
        result.setScope("private");
        result.setOwner("owner");
        return result;
    }
    
    private PageForm pageForm() {
        PageForm result = new PageForm();
        result.setPageNo(1);
        result.setPageSize(10);
        return result;
    }
    
    private AgentVersionListForm versionListForm() {
        AgentVersionListForm result = new AgentVersionListForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setStatus("draft");
        return result;
    }
    
    private AgentVersionForm versionForm() {
        AgentVersionForm result = new AgentVersionForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        return result;
    }
    
    private AgentRuntimeEndpointForm runtimeForm() {
        AgentRuntimeEndpointForm result = new AgentRuntimeEndpointForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setProtocol("a2a");
        result.setVersion(VERSION);
        return result;
    }
    
    private AgentDraftCreateForm createDraftForm() {
        AgentDraftCreateForm result = new AgentDraftCreateForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        result.setCallInterfaces("[]");
        return result;
    }
    
    private AgentDraftUpdateForm updateDraftForm() {
        AgentDraftUpdateForm result = new AgentDraftUpdateForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        result.setCallInterfaces("[]");
        return result;
    }
    
    private AgentLabelsUpdateForm labelsForm() {
        AgentLabelsUpdateForm result = new AgentLabelsUpdateForm();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setLabels("{}");
        return result;
    }
}
