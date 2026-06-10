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
import com.alibaba.nacos.ai.form.skills.admin.SkillBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillDraftCreateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillLabelsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillOnlineForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillScopeForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillSubmitForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.ai.service.skills.SkillUploadRequest;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link SkillInnerHandler}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillInnerHandlerTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String SKILL_NAME = "test-skill";
    
    @Mock
    private SkillOperationService skillOperationService;
    
    private SkillInnerHandler skillInnerHandler;
    
    @BeforeEach
    void setUp() {
        skillInnerHandler = new SkillInnerHandler(skillOperationService);
    }
    
    @Test
    void testGetSkill() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        SkillMeta detail = new SkillMeta();
        detail.setEnable(true);
        when(skillOperationService.getSkillDetail(eq(NAMESPACE_ID), eq(SKILL_NAME)))
            .thenReturn(detail);
        
        SkillMeta result = skillInnerHandler.getSkill(form);
        
        assertNotNull(result);
        assertEquals(true, result.isEnable());
        verify(skillOperationService).getSkillDetail(NAMESPACE_ID, SKILL_NAME);
    }
    
    @Test
    void testGetSkillVersion() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(
            skillOperationService.getSkillVersionDetail(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1")))
            .thenReturn(skill);
        
        Skill result = skillInnerHandler.getSkillVersion(form);
        
        assertEquals(SKILL_NAME, result.getName());
        verify(skillOperationService).getSkillVersionDetail(NAMESPACE_ID, SKILL_NAME, "v1");
    }
    
    @Test
    void testDownloadSkillVersion() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillOperationService.downloadSkillVersion(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1")))
            .thenReturn(skill);
        
        Skill result = skillInnerHandler.downloadSkillVersion(form);
        
        assertEquals(SKILL_NAME, result.getName());
        verify(skillOperationService).downloadSkillVersion(NAMESPACE_ID, SKILL_NAME, "v1");
    }
    
    @Test
    void testDeleteSkill() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillOperationService).deleteSkill(eq(NAMESPACE_ID), eq(SKILL_NAME));
        
        skillInnerHandler.deleteSkill(form);
        
        verify(skillOperationService).deleteSkill(NAMESPACE_ID, SKILL_NAME);
    }
    
    @Test
    void testListSkills() throws NacosException {
        SkillListForm listForm = new SkillListForm();
        listForm.setNamespaceId(NAMESPACE_ID);
        listForm.setSkillName(SKILL_NAME);
        listForm.setSearch("blur");
        listForm.setOrderBy("download_count");
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        SkillSummary item = new SkillSummary();
        item.setName(SKILL_NAME);
        page.setPageItems(List.of(item));
        when(skillOperationService.listSkills(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("blur"),
            eq("download_count"),
            isNull(), isNull(), isNull(), eq(1), eq(10))).thenReturn(page);
        
        Page<SkillSummary> result =
            skillInnerHandler.listSkills(listForm, new AiResourceFilterableForm(), pageForm);
        
        assertEquals(1, result.getTotalCount());
        verify(skillOperationService).listSkills(NAMESPACE_ID, SKILL_NAME, "blur", "download_count",
            null, null, null, 1, 10);
    }
    
    @Test
    void testUploadSkillFromZip() throws NacosException {
        byte[] zipBytes = "test-zip".getBytes();
        SkillUploadRequest request = SkillUploadRequest.builder().namespaceId(NAMESPACE_ID)
            .zipBytes(zipBytes).overwrite(false).build();
        when(skillOperationService.uploadSkillFromZip(request)).thenReturn(SKILL_NAME);
        
        String result = skillInnerHandler.uploadSkillFromZip(request);
        
        assertEquals(SKILL_NAME, result);
        verify(skillOperationService).uploadSkillFromZip(request);
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        SkillDraftCreateForm form = new SkillDraftCreateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setBasedOnVersion("v1");
        form.prepareCreateDraftRequest();
        when(skillOperationService.createDraft(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"), isNull(),
            isNull(), isNull())).thenReturn("v2");
        
        String result = skillInnerHandler.createDraft(form);
        
        assertEquals("v2", result);
        verify(skillOperationService).createDraft(NAMESPACE_ID, SKILL_NAME, "v1", null, null, null);
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillOperationService).deleteDraft(eq(NAMESPACE_ID), eq(SKILL_NAME));
        
        skillInnerHandler.deleteDraft(form);
        
        verify(skillOperationService).deleteDraft(NAMESPACE_ID, SKILL_NAME);
    }
    
    @Test
    void testSubmit() throws NacosException {
        SkillSubmitForm form = new SkillSubmitForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        when(skillOperationService.submit(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1")))
            .thenReturn("pipeline-1");
        
        String result = skillInnerHandler.submit(form);
        
        assertEquals("pipeline-1", result);
        verify(skillOperationService).submit(NAMESPACE_ID, SKILL_NAME, "v1");
    }
    
    @Test
    void testPublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        doNothing().when(skillOperationService).publish(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"),
            eq(true));
        
        skillInnerHandler.publish(form);
        
        verify(skillOperationService).publish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testPublishWithNullUpdateLatestLabel() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(null);
        doNothing().when(skillOperationService).publish(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"),
            eq(true));
        
        skillInnerHandler.publish(form);
        
        verify(skillOperationService).publish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        SkillLabelsUpdateForm form = new SkillLabelsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setLabels("{\"latest\":\"v2\"}");
        doNothing().when(skillOperationService).updateLabels(eq(NAMESPACE_ID), eq(SKILL_NAME),
            any(Map.class));
        
        skillInnerHandler.updateLabels(form);
        
        verify(skillOperationService).updateLabels(eq(NAMESPACE_ID), eq(SKILL_NAME),
            any(Map.class));
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        SkillBizTagsUpdateForm form = new SkillBizTagsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setBizTags("[\"retail\"]");
        doNothing().when(skillOperationService).updateBizTags(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("[\"retail\"]"));
        
        skillInnerHandler.updateBizTags(form);
        
        verify(skillOperationService).updateBizTags(NAMESPACE_ID, SKILL_NAME, "[\"retail\"]");
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        SkillOnlineForm form = new SkillOnlineForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setScope("version");
        form.setVersion("v1");
        doNothing().when(skillOperationService)
            .changeOnlineStatus(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("version"), eq("v1"),
                eq(true));
        
        skillInnerHandler.changeOnlineStatus(form, true);
        
        verify(skillOperationService).changeOnlineStatus(NAMESPACE_ID, SKILL_NAME, "version", "v1",
            true);
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        SkillScopeForm form = new SkillScopeForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setScope("PUBLIC");
        doNothing().when(skillOperationService).updateScope(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("PUBLIC"));
        
        skillInnerHandler.updateScope(form);
        
        verify(skillOperationService).updateScope(NAMESPACE_ID, SKILL_NAME, "PUBLIC");
    }
    
    @Test
    void testForcePublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        doNothing().when(skillOperationService).forcePublish(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("v1"), eq(true));
        
        skillInnerHandler.forcePublish(form);
        
        verify(skillOperationService).forcePublish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testForcePublishWithNullUpdateLatestLabel() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(null);
        doNothing().when(skillOperationService).forcePublish(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("v1"), eq(true));
        
        skillInnerHandler.forcePublish(form);
        
        verify(skillOperationService).forcePublish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        SkillUpdateForm form = new SkillUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setSkillCard(
            "{\"name\":\"test-skill\",\"description\":\"desc\",\"skillMd\":\"# Hi\"}");
        form.setCommitMsg("update");
        doNothing().when(skillOperationService).updateDraft(eq(NAMESPACE_ID), any(Skill.class),
            eq("update"));
        
        skillInnerHandler.updateDraft(form);
        
        verify(skillOperationService).updateDraft(eq(NAMESPACE_ID), any(Skill.class), eq("update"));
    }
    
    @Test
    void testPublishWithFalseUpdateLatestLabel() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(false);
        doNothing().when(skillOperationService).publish(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("v1"), eq(true));
        
        skillInnerHandler.publish(form);
        
        verify(skillOperationService).publish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testForcePublishWithFalseUpdateLatestLabel() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(false);
        doNothing().when(skillOperationService).forcePublish(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("v1"), eq(true));
        
        skillInnerHandler.forcePublish(form);
        
        verify(skillOperationService).forcePublish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testRedraft() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        doNothing().when(skillOperationService).redraft(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"));
        
        skillInnerHandler.redraft(form);
        
        verify(skillOperationService).redraft(NAMESPACE_ID, SKILL_NAME, "v1");
    }
}
