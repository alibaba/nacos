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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.service.a2a.A2aServerOperationService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for A2aInnerHandler.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class A2aInnerHandlerTest {
    
    private static final String NAMESPACE_ID = "test-namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String AGENT_VERSION = "1.0.0";
    
    private static final String REGISTRATION_TYPE = "service";
    
    private static final String SEARCH_TYPE = "blur";
    
    private static final int PAGE_NO = 1;
    
    private static final int PAGE_SIZE = 10;
    
    @Mock
    private A2aServerOperationService a2aServerOperationService;
    
    private A2aInnerHandler a2aInnerHandler;
    
    @BeforeEach
    void setUp() {
        a2aInnerHandler = new A2aInnerHandler(a2aServerOperationService);
    }
    
    @Test
    void registerAgent() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setNamespaceId(NAMESPACE_ID);
        agentCardForm.setRegistrationType(REGISTRATION_TYPE);
        
        doNothing().when(a2aServerOperationService)
                .registerAgent(eq(agentCard), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE));
        
        a2aInnerHandler.registerAgent(agentCard, agentCardForm);
        
        verify(a2aServerOperationService).registerAgent(eq(agentCard), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE));
    }
    
    @Test
    void registerAgentThrowsException() throws NacosException {
        final AgentCard agentCard = new AgentCard();
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setNamespaceId(NAMESPACE_ID);
        agentCardForm.setRegistrationType(REGISTRATION_TYPE);
        
        NacosException expectedException = new NacosException(NacosException.CONFLICT, "Agent already exists");
        doThrow(expectedException).when(a2aServerOperationService)
                .registerAgent(any(AgentCard.class), any(String.class), any(String.class));
        
        NacosException actualException = assertThrows(NacosException.class,
                () -> a2aInnerHandler.registerAgent(agentCard, agentCardForm));
        
        assertEquals(expectedException.getErrCode(), actualException.getErrCode());
        verify(a2aServerOperationService).registerAgent(any(AgentCard.class), any(String.class), any(String.class));
    }
    
    @Test
    void getAgentCardWithVersions() throws NacosException {
        AgentForm form = new AgentForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentName(AGENT_NAME);
        form.setVersion(AGENT_VERSION);
        form.setRegistrationType(REGISTRATION_TYPE);
        
        AgentCardDetailInfo expectedDetailInfo = new AgentCardDetailInfo();
        expectedDetailInfo.setName(AGENT_NAME);
        expectedDetailInfo.setVersion(AGENT_VERSION);
        
        when(a2aServerOperationService.getAgentCard(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(AGENT_VERSION),
                eq(REGISTRATION_TYPE))).thenReturn(expectedDetailInfo);
        
        AgentCardDetailInfo actualDetailInfo = a2aInnerHandler.getAgentCardWithVersions(form);
        
        assertEquals(expectedDetailInfo, actualDetailInfo);
        verify(a2aServerOperationService).getAgentCard(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(AGENT_VERSION),
                eq(REGISTRATION_TYPE));
    }
    
    @Test
    void deleteAgent() throws NacosException {
        AgentForm form = new AgentForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentName(AGENT_NAME);
        form.setVersion(AGENT_VERSION);
        
        doNothing().when(a2aServerOperationService).deleteAgent(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(AGENT_VERSION));
        
        a2aInnerHandler.deleteAgent(form);
        
        verify(a2aServerOperationService).deleteAgent(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(AGENT_VERSION));
    }
    
    @Test
    void updateAgentCard() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        agentCard.setVersion(AGENT_VERSION);
        
        AgentCardUpdateForm form = new AgentCardUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setRegistrationType(REGISTRATION_TYPE);
        form.setSetAsLatest(true);
        
        doNothing().when(a2aServerOperationService)
                .updateAgentCard(eq(agentCard), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE), eq(true));
        
        a2aInnerHandler.updateAgentCard(agentCard, form);
        
        verify(a2aServerOperationService).updateAgentCard(eq(agentCard), eq(NAMESPACE_ID), eq(REGISTRATION_TYPE),
                eq(true));
    }
    
    @Test
    void listAgents() throws NacosException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setNamespaceId(NAMESPACE_ID);
        agentListForm.setAgentName(AGENT_NAME);
        agentListForm.setSearch(SEARCH_TYPE);
        
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(PAGE_NO);
        pageForm.setPageSize(PAGE_SIZE);
        
        Page<AgentCardVersionInfo> expectedPage = new Page<>();
        List<AgentCardVersionInfo> items = new ArrayList<>();
        AgentCardVersionInfo info = new AgentCardVersionInfo();
        info.setName(AGENT_NAME);
        items.add(info);
        expectedPage.setPageItems(items);
        expectedPage.setPageNumber(PAGE_NO);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(1);
        
        when(a2aServerOperationService.listAgents(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(SEARCH_TYPE), eq(PAGE_NO),
                eq(PAGE_SIZE))).thenReturn(expectedPage);
        
        Page<AgentCardVersionInfo> actualPage = a2aInnerHandler.listAgents(agentListForm, pageForm);
        
        assertEquals(expectedPage, actualPage);
        verify(a2aServerOperationService).listAgents(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(SEARCH_TYPE), eq(PAGE_NO),
                eq(PAGE_SIZE));
    }
    
    @Test
    void listAgentVersions() throws NacosException {
        List<AgentVersionDetail> expectedVersions = new ArrayList<>();
        AgentVersionDetail version = new AgentVersionDetail();
        version.setVersion(AGENT_VERSION);
        expectedVersions.add(version);
        
        when(a2aServerOperationService.listAgentVersions(eq(NAMESPACE_ID), eq(AGENT_NAME))).thenReturn(
                expectedVersions);
        
        List<AgentVersionDetail> actualVersions = a2aInnerHandler.listAgentVersions(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(expectedVersions, actualVersions);
        verify(a2aServerOperationService).listAgentVersions(eq(NAMESPACE_ID), eq(AGENT_NAME));
    }
}