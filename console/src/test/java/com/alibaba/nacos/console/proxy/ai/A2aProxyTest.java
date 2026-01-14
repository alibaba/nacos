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

package com.alibaba.nacos.console.proxy.ai;

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
import com.alibaba.nacos.console.handler.ai.A2aHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for A2aProxy.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class A2aProxyTest {
    
    private static final String NAMESPACE_ID = "test-namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String AGENT_VERSION = "1.0.0";
    
    @Mock
    private A2aHandler a2aHandler;
    
    private A2aProxy a2aProxy;
    
    @BeforeEach
    public void setUp() {
        a2aProxy = new A2aProxy(a2aHandler);
    }
    
    @Test
    public void testRegisterAgent() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        AgentCardForm agentCardForm = new AgentCardForm();
        
        doNothing().when(a2aHandler).registerAgent(agentCard, agentCardForm);
        
        a2aProxy.registerAgent(agentCard, agentCardForm);
        
        verify(a2aHandler, times(1)).registerAgent(agentCard, agentCardForm);
    }
    
    @Test
    public void testRegisterAgentThrowsException() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        AgentCardForm agentCardForm = new AgentCardForm();
        NacosException expectedException = new NacosException(NacosException.INVALID_PARAM, "Invalid agent card");
        
        doThrow(expectedException).when(a2aHandler).registerAgent(agentCard, agentCardForm);
        
        NacosException actualException = assertThrows(NacosException.class,
                () -> a2aProxy.registerAgent(agentCard, agentCardForm));
        
        assertEquals(expectedException.getErrCode(), actualException.getErrCode());
        assertEquals(expectedException.getMessage(), actualException.getMessage());
        verify(a2aHandler, times(1)).registerAgent(agentCard, agentCardForm);
    }
    
    @Test
    public void testGetAgentCard() throws NacosException {
        AgentForm form = new AgentForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentName(AGENT_NAME);
        
        AgentCardDetailInfo expectedDetailInfo = new AgentCardDetailInfo();
        expectedDetailInfo.setName(AGENT_NAME);
        
        when(a2aHandler.getAgentCardWithVersions(form)).thenReturn(expectedDetailInfo);
        
        AgentCardDetailInfo actualDetailInfo = a2aProxy.getAgentCard(form);
        
        assertNotNull(actualDetailInfo);
        assertEquals(expectedDetailInfo, actualDetailInfo);
        verify(a2aHandler, times(1)).getAgentCardWithVersions(form);
    }
    
    @Test
    public void testDeleteAgent() throws NacosException {
        AgentForm form = new AgentForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentName(AGENT_NAME);
        
        doNothing().when(a2aHandler).deleteAgent(form);
        
        a2aProxy.deleteAgent(form);
        
        verify(a2aHandler, times(1)).deleteAgent(form);
    }
    
    @Test
    public void testUpdateAgentCard() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(AGENT_NAME);
        AgentCardUpdateForm form = new AgentCardUpdateForm();
        form.setVersion(AGENT_VERSION);
        
        doNothing().when(a2aHandler).updateAgentCard(agentCard, form);
        
        a2aProxy.updateAgentCard(agentCard, form);
        
        verify(a2aHandler, times(1)).updateAgentCard(agentCard, form);
    }
    
    @Test
    public void testListAgents() throws NacosException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setNamespaceId(NAMESPACE_ID);
        agentListForm.setAgentName(AGENT_NAME);
        
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        
        Page<AgentCardVersionInfo> expectedPage = new Page<>();
        List<AgentCardVersionInfo> items = new ArrayList<>();
        AgentCardVersionInfo info = new AgentCardVersionInfo();
        info.setName(AGENT_NAME);
        items.add(info);
        expectedPage.setPageItems(items);
        expectedPage.setPageNumber(1);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(1);
        
        when(a2aHandler.listAgents(agentListForm, pageForm)).thenReturn(expectedPage);
        
        Page<AgentCardVersionInfo> actualPage = a2aProxy.listAgents(agentListForm, pageForm);
        
        assertNotNull(actualPage);
        assertEquals(expectedPage, actualPage);
        verify(a2aHandler, times(1)).listAgents(agentListForm, pageForm);
    }
    
    @Test
    public void testListAgentVersions() throws NacosException {
        List<AgentVersionDetail> expectedVersions = new ArrayList<>();
        AgentVersionDetail version = new AgentVersionDetail();
        version.setVersion(AGENT_VERSION);
        expectedVersions.add(version);
        
        when(a2aHandler.listAgentVersions(NAMESPACE_ID, AGENT_NAME)).thenReturn(expectedVersions);
        
        List<AgentVersionDetail> actualVersions = a2aProxy.listAgentVersions(NAMESPACE_ID, AGENT_NAME);
        
        assertNotNull(actualVersions);
        assertEquals(expectedVersions, actualVersions);
        verify(a2aHandler, times(1)).listAgentVersions(NAMESPACE_ID, AGENT_NAME);
    }
}