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

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSpecProxyTest {
    
    private static final String NS = "test-ns";
    
    private static final String AGENT_SPEC_NAME = "test-agentspec";
    
    @Mock
    private AgentSpecHandler agentSpecHandler;
    
    private AgentSpecProxy agentSpecProxy;
    
    @BeforeEach
    void setUp() {
        agentSpecProxy = new AgentSpecProxy(agentSpecHandler);
    }
    
    @Test
    void testGetAgentSpec() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setName(AGENT_SPEC_NAME);
        when(agentSpecHandler.getAgentSpec(form)).thenReturn(meta);
        
        AgentSpecMeta result = agentSpecProxy.getAgentSpec(form);
        
        assertEquals(AGENT_SPEC_NAME, result.getName());
        verify(agentSpecHandler).getAgentSpec(form);
    }
    
    @Test
    void testGetAgentSpecVersion() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        AgentSpec spec = new AgentSpec();
        spec.setName(AGENT_SPEC_NAME);
        when(agentSpecHandler.getAgentSpecVersion(form)).thenReturn(spec);
        
        AgentSpec result = agentSpecProxy.getAgentSpecVersion(form);
        
        assertEquals(AGENT_SPEC_NAME, result.getName());
        verify(agentSpecHandler).getAgentSpecVersion(form);
    }
    
    @Test
    void testDeleteAgentSpec() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        doNothing().when(agentSpecHandler).deleteAgentSpec(form);
        
        agentSpecProxy.deleteAgentSpec(form);
        
        verify(agentSpecHandler).deleteAgentSpec(form);
    }
    
    @Test
    void testListAgentSpecs() throws NacosException {
        AgentSpecListForm listForm = new AgentSpecListForm();
        AiResourceFilterableForm filterForm = new AiResourceFilterableForm();
        PageForm pageForm = new PageForm();
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(2);
        page.setPageItems(List.of(new AgentSpecSummary(), new AgentSpecSummary()));
        when(agentSpecHandler.listAgentSpecs(listForm, filterForm, pageForm))
            .thenReturn(page);
        
        Page<AgentSpecSummary> result = agentSpecProxy.listAgentSpecs(listForm,
            filterForm, pageForm);
        
        assertEquals(2, result.getTotalCount());
        verify(agentSpecHandler).listAgentSpecs(listForm, filterForm, pageForm);
    }
    
    @Test
    void testUploadAgentSpecFromZipDefaultOverwrite() throws NacosException {
        byte[] zipBytes = new byte[] {1, 2, 3};
        when(agentSpecHandler.uploadAgentSpecFromZip(NS, zipBytes, false))
            .thenReturn(AGENT_SPEC_NAME);
        
        String result = agentSpecProxy.uploadAgentSpecFromZip(NS, zipBytes);
        
        assertEquals(AGENT_SPEC_NAME, result);
        verify(agentSpecHandler).uploadAgentSpecFromZip(NS, zipBytes, false);
    }
    
    @Test
    void testUploadAgentSpecFromZipWithOverwrite() throws NacosException {
        byte[] zipBytes = new byte[] {1, 2, 3};
        when(agentSpecHandler.uploadAgentSpecFromZip(NS, zipBytes, true))
            .thenReturn(AGENT_SPEC_NAME);
        
        String result = agentSpecProxy.uploadAgentSpecFromZip(NS, zipBytes, true);
        
        assertEquals(AGENT_SPEC_NAME, result);
        verify(agentSpecHandler).uploadAgentSpecFromZip(NS, zipBytes, true);
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        AgentSpecDraftCreateForm form = new AgentSpecDraftCreateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        when(agentSpecHandler.createDraft(form)).thenReturn("v1-draft");
        
        String result = agentSpecProxy.createDraft(form);
        
        assertEquals("v1-draft", result);
        verify(agentSpecHandler).createDraft(form);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        AgentSpecUpdateForm form = new AgentSpecUpdateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        doNothing().when(agentSpecHandler).updateDraft(form);
        
        agentSpecProxy.updateDraft(form);
        
        verify(agentSpecHandler).updateDraft(form);
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        doNothing().when(agentSpecHandler).deleteDraft(form);
        
        agentSpecProxy.deleteDraft(form);
        
        verify(agentSpecHandler).deleteDraft(form);
    }
    
    @Test
    void testSubmit() throws NacosException {
        AgentSpecSubmitForm form = new AgentSpecSubmitForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        when(agentSpecHandler.submit(form)).thenReturn("reviewing");
        
        String result = agentSpecProxy.submit(form);
        
        assertEquals("reviewing", result);
        verify(agentSpecHandler).submit(form);
    }
    
    @Test
    void testPublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        doNothing().when(agentSpecHandler).publish(form);
        
        agentSpecProxy.publish(form);
        
        verify(agentSpecHandler).publish(form);
    }
    
    @Test
    void testForcePublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        doNothing().when(agentSpecHandler).forcePublish(form);
        
        agentSpecProxy.forcePublish(form);
        
        verify(agentSpecHandler).forcePublish(form);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        AgentSpecLabelsUpdateForm form = new AgentSpecLabelsUpdateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        doNothing().when(agentSpecHandler).updateLabels(form);
        
        agentSpecProxy.updateLabels(form);
        
        verify(agentSpecHandler).updateLabels(form);
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        AgentSpecBizTagsUpdateForm form = new AgentSpecBizTagsUpdateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        doNothing().when(agentSpecHandler).updateBizTags(form);
        
        agentSpecProxy.updateBizTags(form);
        
        verify(agentSpecHandler).updateBizTags(form);
    }
    
    @Test
    public void testRedraft() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        
        doNothing().when(agentSpecHandler).redraft(form);
        
        agentSpecProxy.redraft(form);
        
        verify(agentSpecHandler, times(1)).redraft(form);
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        AgentSpecOnlineForm form = new AgentSpecOnlineForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        doNothing().when(agentSpecHandler).changeOnlineStatus(form, true);
        
        agentSpecProxy.changeOnlineStatus(form, true);
        
        verify(agentSpecHandler).changeOnlineStatus(form, true);
    }
    
    @Test
    void testOnline() throws NacosException {
        AgentSpecOnlineForm form = new AgentSpecOnlineForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        doNothing().when(agentSpecHandler).changeOnlineStatus(form, true);
        
        agentSpecProxy.online(form);
        
        verify(agentSpecHandler).changeOnlineStatus(form, true);
    }
    
    @Test
    void testOffline() throws NacosException {
        AgentSpecOnlineForm form = new AgentSpecOnlineForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        doNothing().when(agentSpecHandler).changeOnlineStatus(form, false);
        
        agentSpecProxy.offline(form);
        
        verify(agentSpecHandler).changeOnlineStatus(form, false);
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        AgentSpecScopeForm form = new AgentSpecScopeForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        doNothing().when(agentSpecHandler).updateScope(form);
        
        agentSpecProxy.updateScope(form);
        
        verify(agentSpecHandler).updateScope(form);
    }
}
