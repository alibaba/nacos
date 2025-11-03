/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for A2aRemoteHandler.
 *
 * @author nacos
 */
public class A2aRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    private static final String NAMESPACE_ID = "test-namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String REGISTRATION_TYPE = "service";
    
    private static final String SEARCH_TYPE_BLUR = Constants.MCP_LIST_SEARCH_BLUR;
    
    private static final String SEARCH_TYPE_ACCURATE = "accurate";
    
    private static final int PAGE_NO = 1;
    
    private static final int PAGE_SIZE = 10;
    
    private A2aRemoteHandler a2aRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithAi();
        a2aRemoteHandler = new A2aRemoteHandler(clientHolder);
    }
    
    @Test
    void registerAgent() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        
        AgentCardForm form = new AgentCardForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setRegistrationType(REGISTRATION_TYPE);
        
        when(aiMaintainerService.registerAgent(eq(agentCard), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE))).thenReturn(true);
        a2aRemoteHandler.registerAgent(agentCard, form);
        verify(aiMaintainerService).registerAgent(eq(agentCard), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE));
    }
    
    @Test
    void getAgentCardWithVersions() throws NacosException {
        AgentForm form = new AgentForm();
        form.setAgentName(AGENT_NAME);
        form.setNamespaceId(NAMESPACE_ID);
        form.setRegistrationType(REGISTRATION_TYPE);
        
        AgentCardDetailInfo expectedDetailInfo = new AgentCardDetailInfo();
        expectedDetailInfo.setName(AGENT_NAME);
        
        when(aiMaintainerService.getAgentCard(eq(AGENT_NAME), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE))).thenReturn(
                expectedDetailInfo);
        
        AgentCardDetailInfo actualDetailInfo = a2aRemoteHandler.getAgentCardWithVersions(form);
        
        assertEquals(expectedDetailInfo, actualDetailInfo);
        verify(aiMaintainerService).getAgentCard(eq(AGENT_NAME), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE));
    }
    
    @Test
    void deleteAgent() throws NacosException {
        AgentForm form = new AgentForm();
        form.setAgentName(AGENT_NAME);
        form.setNamespaceId(NAMESPACE_ID);
        
        when(aiMaintainerService.deleteAgent(eq(AGENT_NAME), eq(NAMESPACE_ID))).thenReturn(true);
        
        a2aRemoteHandler.deleteAgent(form);
        
        verify(aiMaintainerService).deleteAgent(eq(AGENT_NAME), eq(NAMESPACE_ID));
    }
    
    @Test
    void updateAgentCard() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        
        AgentCardUpdateForm form = new AgentCardUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSetAsLatest(true);
        form.setRegistrationType(REGISTRATION_TYPE);
        
        when(aiMaintainerService.updateAgentCard(eq(agentCard), eq(NAMESPACE_ID), eq(true),
                eq(REGISTRATION_TYPE))).thenReturn(true);
        
        a2aRemoteHandler.updateAgentCard(agentCard, form);
        
        verify(aiMaintainerService).updateAgentCard(eq(agentCard), eq(NAMESPACE_ID), eq(true), eq(REGISTRATION_TYPE));
    }
    
    @Test
    void listAgentsWithBlurSearch() throws NacosException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setNamespaceId(NAMESPACE_ID);
        agentListForm.setAgentName(AGENT_NAME);
        agentListForm.setSearch(SEARCH_TYPE_BLUR);
        
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(PAGE_NO);
        pageForm.setPageSize(PAGE_SIZE);
        
        Page<AgentCardVersionInfo> expectedPage = new Page<>();
        
        when(aiMaintainerService.searchAgentCardsByName(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(PAGE_NO),
                eq(PAGE_SIZE))).thenReturn(expectedPage);
        
        Page<AgentCardVersionInfo> actualPage = a2aRemoteHandler.listAgents(agentListForm, pageForm);
        
        assertEquals(expectedPage, actualPage);
        verify(aiMaintainerService).searchAgentCardsByName(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(PAGE_NO),
                eq(PAGE_SIZE));
    }
    
    @Test
    void listAgentsWithAccurateSearch() throws NacosException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setNamespaceId(NAMESPACE_ID);
        agentListForm.setAgentName(AGENT_NAME);
        agentListForm.setSearch(SEARCH_TYPE_ACCURATE);
        
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(PAGE_NO);
        pageForm.setPageSize(PAGE_SIZE);
        
        Page<AgentCardVersionInfo> expectedPage = new Page<>();
        
        when(aiMaintainerService.listAgentCards(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(PAGE_NO),
                eq(PAGE_SIZE))).thenReturn(expectedPage);
        
        Page<AgentCardVersionInfo> actualPage = a2aRemoteHandler.listAgents(agentListForm, pageForm);
        
        assertEquals(expectedPage, actualPage);
        verify(aiMaintainerService).listAgentCards(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(PAGE_NO), eq(PAGE_SIZE));
    }
    
    @Test
    void listAgentVersions() throws NacosException {
        List<AgentVersionDetail> expectedVersions = new ArrayList<>();
        AgentVersionDetail version = new AgentVersionDetail();
        version.setVersion("1.0.0");
        expectedVersions.add(version);
        
        when(aiMaintainerService.listAllVersionOfAgent(eq(AGENT_NAME), eq(NAMESPACE_ID))).thenReturn(expectedVersions);
        
        List<AgentVersionDetail> actualVersions = a2aRemoteHandler.listAgentVersions(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(expectedVersions, actualVersions);
        verify(aiMaintainerService).listAllVersionOfAgent(eq(AGENT_NAME), eq(NAMESPACE_ID));
    }
}