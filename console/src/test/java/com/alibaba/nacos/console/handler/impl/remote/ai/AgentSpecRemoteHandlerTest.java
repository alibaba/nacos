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
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.maintainer.client.ai.AgentSpecMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AgentSpecRemoteHandler}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentSpecRemoteHandlerTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String AGENT_SPEC_NAME = "test-agentspec";
    
    @Mock
    private NacosMaintainerClientHolder clientHolder;
    
    @Mock
    private AiMaintainerService aiMaintainerService;
    
    @Mock
    private AgentSpecMaintainerService agentSpecMaintainerService;
    
    private AgentSpecRemoteHandler agentSpecRemoteHandler;
    
    @BeforeEach
    void setUp() {
        when(clientHolder.getAiMaintainerService()).thenReturn(aiMaintainerService);
        when(aiMaintainerService.agentSpec()).thenReturn(agentSpecMaintainerService);
        agentSpecRemoteHandler = new AgentSpecRemoteHandler(clientHolder);
    }
    
    @Test
    void testGetAgentSpecVersion() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName(AGENT_SPEC_NAME);
        when(agentSpecMaintainerService.getAgentSpecVersionDetail(eq(NAMESPACE_ID),
            eq(AGENT_SPEC_NAME),
            eq("v1"))).thenReturn(agentSpec);
        
        AgentSpec result = agentSpecRemoteHandler.getAgentSpecVersion(form);
        
        assertEquals(AGENT_SPEC_NAME, result.getName());
        verify(agentSpecMaintainerService).getAgentSpecVersionDetail(NAMESPACE_ID, AGENT_SPEC_NAME,
            "v1");
    }
    
    @Test
    void testGetAgentSpec() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        AgentSpecMeta detail = new AgentSpecMeta();
        detail.setBizTags("[\"finance\"]");
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(eq(NAMESPACE_ID),
            eq(AGENT_SPEC_NAME))).thenReturn(
                detail);
        
        AgentSpecMeta result = agentSpecRemoteHandler.getAgentSpec(form);
        
        assertNotNull(result);
        assertEquals("[\"finance\"]", result.getBizTags());
    }
    
    @Test
    void testListAgentSpecs() throws NacosException {
        AgentSpecListForm form = new AgentSpecListForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        AgentSpecSummary item = new AgentSpecSummary();
        item.setName(AGENT_SPEC_NAME);
        item.setBizTags("[\"finance\"]");
        Page<AgentSpecSummary> page = new Page<>();
        page.setPageItems(java.util.List.of(item));
        page.setTotalCount(1);
        when(agentSpecMaintainerService.listAgentSpecAdminItems(eq(NAMESPACE_ID),
            eq(AGENT_SPEC_NAME), eq(null),
            isNull(), isNull(), isNull(), eq(1), eq(10))).thenReturn(page);
        
        Page<AgentSpecSummary> result =
            agentSpecRemoteHandler.listAgentSpecs(form, new AiResourceFilterableForm(),
                pageForm);
        
        assertEquals("[\"finance\"]", result.getPageItems().get(0).getBizTags());
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        AgentSpecScopeForm form = new AgentSpecScopeForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setScope("PUBLIC");
        when(agentSpecMaintainerService.updateScope(eq(NAMESPACE_ID), eq(AGENT_SPEC_NAME),
            eq("PUBLIC"))).thenReturn(
                true);
        
        agentSpecRemoteHandler.updateScope(form);
        
        verify(agentSpecMaintainerService).updateScope(NAMESPACE_ID, AGENT_SPEC_NAME, "PUBLIC");
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        AgentSpecBizTagsUpdateForm form = new AgentSpecBizTagsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setBizTags("[\"finance\"]");
        when(agentSpecMaintainerService.updateBizTags(eq(NAMESPACE_ID), eq(AGENT_SPEC_NAME),
            eq("[\"finance\"]"))).thenReturn(true);
        
        agentSpecRemoteHandler.updateBizTags(form);
        
        verify(agentSpecMaintainerService).updateBizTags(NAMESPACE_ID, AGENT_SPEC_NAME,
            "[\"finance\"]");
    }
    
    @Test
    void testForcePublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        when(
            agentSpecMaintainerService.forcePublish(eq(NAMESPACE_ID), eq(AGENT_SPEC_NAME), eq("v1"),
                eq(true)))
            .thenReturn(true);
        
        agentSpecRemoteHandler.forcePublish(form);
        
        verify(agentSpecMaintainerService).forcePublish(NAMESPACE_ID, AGENT_SPEC_NAME, "v1", true);
    }
    
    @Test
    void testDeleteAgentSpec() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        when(agentSpecMaintainerService.deleteAgentSpec(NAMESPACE_ID, AGENT_SPEC_NAME))
            .thenReturn(true);
        
        agentSpecRemoteHandler.deleteAgentSpec(form);
        
        verify(agentSpecMaintainerService).deleteAgentSpec(NAMESPACE_ID, AGENT_SPEC_NAME);
    }
    
    @Test
    void testListAgentSpecsReturnsNullFromService() throws NacosException {
        AgentSpecListForm form = new AgentSpecListForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        when(agentSpecMaintainerService.listAgentSpecAdminItems(eq(NAMESPACE_ID),
            eq(AGENT_SPEC_NAME), isNull(), isNull(), isNull(), isNull(), eq(1), eq(10)))
            .thenReturn(null);
        
        Page<AgentSpecSummary> result =
            agentSpecRemoteHandler.listAgentSpecs(form, new AiResourceFilterableForm(), pageForm);
        
        assertNotNull(result);
        assertEquals(0, result.getTotalCount());
    }
    
    @Test
    void testUploadAgentSpecFromZip() throws NacosException {
        byte[] zip = new byte[] {1, 2, 3};
        when(agentSpecMaintainerService.uploadAgentSpecFromZip(NAMESPACE_ID, zip, true))
            .thenReturn(AGENT_SPEC_NAME);
        
        String result = agentSpecRemoteHandler.uploadAgentSpecFromZip(NAMESPACE_ID, zip, true);
        
        assertEquals(AGENT_SPEC_NAME, result);
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        AgentSpecDraftCreateForm form = new AgentSpecDraftCreateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setBasedOnVersion("v1");
        form.setTargetVersion("v2");
        when(agentSpecMaintainerService.createDraft(NAMESPACE_ID, AGENT_SPEC_NAME, "v1", "v2"))
            .thenReturn("v2-draft");
        
        String result = agentSpecRemoteHandler.createDraft(form);
        
        assertEquals("v2-draft", result);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        AgentSpecUpdateForm form = new AgentSpecUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setAgentSpecCard("{\"name\":\"test\"}");
        form.setSetAsLatest(true);
        when(agentSpecMaintainerService.updateDraft(NAMESPACE_ID, "{\"name\":\"test\"}", true))
            .thenReturn(true);
        
        agentSpecRemoteHandler.updateDraft(form);
        
        verify(agentSpecMaintainerService).updateDraft(NAMESPACE_ID, "{\"name\":\"test\"}", true);
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        when(agentSpecMaintainerService.deleteDraft(NAMESPACE_ID, AGENT_SPEC_NAME))
            .thenReturn(true);
        
        agentSpecRemoteHandler.deleteDraft(form);
        
        verify(agentSpecMaintainerService).deleteDraft(NAMESPACE_ID, AGENT_SPEC_NAME);
    }
    
    @Test
    void testSubmit() throws NacosException {
        AgentSpecSubmitForm form = new AgentSpecSubmitForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        when(agentSpecMaintainerService.submit(NAMESPACE_ID, AGENT_SPEC_NAME, "v1"))
            .thenReturn("reviewing");
        
        String result = agentSpecRemoteHandler.submit(form);
        
        assertEquals("reviewing", result);
    }
    
    @Test
    void testPublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        when(agentSpecMaintainerService.publish(NAMESPACE_ID, AGENT_SPEC_NAME, "v1", true))
            .thenReturn(true);
        
        agentSpecRemoteHandler.publish(form);
        
        verify(agentSpecMaintainerService).publish(NAMESPACE_ID, AGENT_SPEC_NAME, "v1", true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        AgentSpecLabelsUpdateForm form = new AgentSpecLabelsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setLabels("{\"env\":\"prod\"}");
        when(agentSpecMaintainerService.updateLabels(NAMESPACE_ID, AGENT_SPEC_NAME,
            "{\"env\":\"prod\"}")).thenReturn(true);
        
        agentSpecRemoteHandler.updateLabels(form);
        
        verify(agentSpecMaintainerService).updateLabels(NAMESPACE_ID, AGENT_SPEC_NAME,
            "{\"env\":\"prod\"}");
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        AgentSpecOnlineForm form = new AgentSpecOnlineForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        form.setScope("PUBLIC");
        when(agentSpecMaintainerService.changeOnlineStatus(NAMESPACE_ID, AGENT_SPEC_NAME,
            "PUBLIC", "v1", true)).thenReturn(true);
        
        agentSpecRemoteHandler.changeOnlineStatus(form, true);
        
        verify(agentSpecMaintainerService).changeOnlineStatus(NAMESPACE_ID, AGENT_SPEC_NAME,
            "PUBLIC", "v1", true);
    }
    
    @Test
    void testRedraft() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        when(agentSpecMaintainerService.redraft(eq(NAMESPACE_ID), eq(AGENT_SPEC_NAME), eq("v1")))
            .thenReturn(true);
        
        agentSpecRemoteHandler.redraft(form);
        
        verify(agentSpecMaintainerService).redraft(NAMESPACE_ID, AGENT_SPEC_NAME, "v1");
    }
}
