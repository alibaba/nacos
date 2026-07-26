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
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSpecInnerHandlerTest {
    
    private static final String NS = "test-ns";
    
    private static final String NAME = "test-agentspec";
    
    @Mock
    private AgentSpecOperationService agentSpecOperationService;
    
    private AgentSpecInnerHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentSpecInnerHandler(agentSpecOperationService);
    }
    
    @Test
    void testGetAgentSpec() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setName(NAME);
        when(agentSpecOperationService.getAgentSpecDetail(NS, NAME, "v1"))
            .thenReturn(meta);
        
        AgentSpecMeta result = handler.getAgentSpec(form);
        
        assertEquals(NAME, result.getName());
    }
    
    @Test
    void testGetAgentSpecVersion() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        AgentSpec spec = new AgentSpec();
        spec.setName(NAME);
        when(agentSpecOperationService.getAgentSpecVersionDetail(NS, NAME, "v1"))
            .thenReturn(spec);
        
        AgentSpec result = handler.getAgentSpecVersion(form);
        
        assertEquals(NAME, result.getName());
    }
    
    @Test
    void testDeleteAgentSpec() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        doNothing().when(agentSpecOperationService).deleteAgentSpec(NS, NAME);
        
        handler.deleteAgentSpec(form);
        
        verify(agentSpecOperationService).deleteAgentSpec(NS, NAME);
    }
    
    @Test
    void testListAgentSpecs() throws NacosException {
        AgentSpecListForm listForm = new AgentSpecListForm();
        listForm.setNamespaceId(NS);
        listForm.setAgentSpecName(NAME);
        listForm.setSearch("blur");
        listForm.setOrderBy("name");
        AiResourceFilterableForm filterForm = new AiResourceFilterableForm();
        filterForm.setOwner("alice");
        filterForm.setScope("PUBLIC");
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(1);
        when(agentSpecOperationService.listAgentSpecs(NS, NAME, "blur", "name",
            "alice", "PUBLIC", 1, 10)).thenReturn(page);
        
        Page<AgentSpecSummary> result = handler.listAgentSpecs(listForm, filterForm,
            pageForm);
        
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    void testUploadAgentSpecFromZip() throws NacosException {
        byte[] zip = new byte[] {1, 2, 3};
        when(agentSpecOperationService.uploadAgentSpecFromZip(NS, zip, true))
            .thenReturn(NAME);
        
        String result = handler.uploadAgentSpecFromZip(NS, zip, true);
        
        assertEquals(NAME, result);
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        AgentSpecDraftCreateForm form = new AgentSpecDraftCreateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setBasedOnVersion("v1");
        form.setTargetVersion("v2");
        when(agentSpecOperationService.createDraft(NS, NAME, "v1", "v2"))
            .thenReturn("v2-draft");
        
        String result = handler.createDraft(form);
        
        assertEquals("v2-draft", result);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        AgentSpecUpdateForm form = new AgentSpecUpdateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setAgentSpecCard("{\"name\":\"test-agentspec\",\"version\":\"v1-draft\"}");
        doNothing().when(agentSpecOperationService).updateDraft(eq(NS), any(AgentSpec.class));
        
        handler.updateDraft(form);
        
        verify(agentSpecOperationService).updateDraft(eq(NS), any(AgentSpec.class));
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        doNothing().when(agentSpecOperationService).deleteDraft(NS, NAME);
        
        handler.deleteDraft(form);
        
        verify(agentSpecOperationService).deleteDraft(NS, NAME);
    }
    
    @Test
    void testSubmit() throws NacosException {
        AgentSpecSubmitForm form = new AgentSpecSubmitForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        when(agentSpecOperationService.submit(NS, NAME, "v1")).thenReturn("reviewing");
        
        String result = handler.submit(form);
        
        assertEquals("reviewing", result);
    }
    
    @Test
    void testPublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        doNothing().when(agentSpecOperationService).publish(NS, NAME, "v1", true);
        
        handler.publish(form);
        
        verify(agentSpecOperationService).publish(NS, NAME, "v1", true);
    }
    
    @Test
    void testPublishWithNullUpdateLatestLabel() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(null);
        doNothing().when(agentSpecOperationService).publish(NS, NAME, "v1", true);
        
        handler.publish(form);
        
        verify(agentSpecOperationService).publish(NS, NAME, "v1", true);
    }
    
    @Test
    void testForcePublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        doNothing().when(agentSpecOperationService).forcePublish(NS, NAME, "v1", true);
        
        handler.forcePublish(form);
        
        verify(agentSpecOperationService).forcePublish(NS, NAME, "v1", true);
    }
    
    @Test
    void testForcePublishWithNullUpdateLatestLabel() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(null);
        doNothing().when(agentSpecOperationService).forcePublish(NS, NAME, "v1", true);
        
        handler.forcePublish(form);
        
        verify(agentSpecOperationService).forcePublish(NS, NAME, "v1", true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        AgentSpecLabelsUpdateForm form = new AgentSpecLabelsUpdateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setLabels("{\"env\":\"prod\"}");
        doNothing().when(agentSpecOperationService).updateLabels(eq(NS), eq(NAME),
            any());
        
        handler.updateLabels(form);
        
        verify(agentSpecOperationService).updateLabels(eq(NS), eq(NAME), any());
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        AgentSpecBizTagsUpdateForm form = new AgentSpecBizTagsUpdateForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setBizTags("[\"finance\"]");
        doNothing().when(agentSpecOperationService).updateBizTags(NS, NAME,
            "[\"finance\"]");
        
        handler.updateBizTags(form);
        
        verify(agentSpecOperationService).updateBizTags(NS, NAME, "[\"finance\"]");
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        AgentSpecOnlineForm form = new AgentSpecOnlineForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        form.setScope("PUBLIC");
        doNothing().when(agentSpecOperationService).changeOnlineStatus(NS, NAME,
            "PUBLIC", "v1", true);
        
        handler.changeOnlineStatus(form, true);
        
        verify(agentSpecOperationService).changeOnlineStatus(NS, NAME, "PUBLIC",
            "v1", true);
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        AgentSpecScopeForm form = new AgentSpecScopeForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setScope("PUBLIC");
        doNothing().when(agentSpecOperationService).updateScope(NS, NAME, "PUBLIC");
        
        handler.updateScope(form);
        
        verify(agentSpecOperationService).updateScope(NS, NAME, "PUBLIC");
    }
    
    @Test
    void testRedraft() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NS);
        form.setAgentSpecName(NAME);
        form.setVersion("v1");
        doNothing().when(agentSpecOperationService).redraft(eq(NS), eq(NAME),
            eq("v1"));
        
        handler.redraft(form);
        
        verify(agentSpecOperationService).redraft(NS, NAME, "v1");
    }
}
