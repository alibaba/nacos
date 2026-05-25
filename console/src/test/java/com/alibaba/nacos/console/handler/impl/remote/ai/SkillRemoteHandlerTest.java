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
import com.alibaba.nacos.ai.service.skills.SkillUploadRequest;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link SkillRemoteHandler}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillRemoteHandlerTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String SKILL_NAME = "test-skill";
    
    @Mock
    private NacosMaintainerClientHolder clientHolder;
    
    @Mock
    private AiMaintainerService aiMaintainerService;
    
    @Mock
    private SkillMaintainerService skillMaintainerService;
    
    private SkillRemoteHandler skillRemoteHandler;
    
    @BeforeEach
    void setUp() {
        when(clientHolder.getAiMaintainerService()).thenReturn(aiMaintainerService);
        when(aiMaintainerService.skill()).thenReturn(skillMaintainerService);
        skillRemoteHandler = new SkillRemoteHandler(clientHolder);
    }
    
    @Test
    void testGetSkill() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        SkillMeta skillMeta = new SkillMeta();
        skillMeta.setName(SKILL_NAME);
        when(skillMaintainerService.getSkillMeta(eq(NAMESPACE_ID), eq(SKILL_NAME)))
            .thenReturn(skillMeta);
        
        SkillMeta result = skillRemoteHandler.getSkill(form);
        
        assertNotNull(result);
        assertEquals(SKILL_NAME, result.getName());
    }
    
    @Test
    void testGetSkillVersion() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillMaintainerService.getSkillVersionDetail(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("v1"))).thenReturn(
                skill);
        
        Skill result = skillRemoteHandler.getSkillVersion(form);
        
        assertEquals(SKILL_NAME, result.getName());
        verify(skillMaintainerService).getSkillVersionDetail(NAMESPACE_ID, SKILL_NAME, "v1");
    }
    
    @Test
    void testDownloadSkillVersionDelegatesToGetSkillVersion() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillMaintainerService.getSkillVersionDetail(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("v1"))).thenReturn(
                skill);
        
        Skill result = skillRemoteHandler.downloadSkillVersion(form);
        
        assertEquals(SKILL_NAME, result.getName());
        verify(skillMaintainerService).getSkillVersionDetail(NAMESPACE_ID, SKILL_NAME, "v1");
    }
    
    @Test
    void testDeleteSkill() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        when(skillMaintainerService.deleteSkill(eq(NAMESPACE_ID), eq(SKILL_NAME))).thenReturn(true);
        
        skillRemoteHandler.deleteSkill(form);
        
        verify(skillMaintainerService).deleteSkill(NAMESPACE_ID, SKILL_NAME);
    }
    
    @Test
    void testListSkills() throws NacosException {
        SkillListForm listForm = new SkillListForm();
        listForm.setNamespaceId(NAMESPACE_ID);
        listForm.setSkillName(SKILL_NAME);
        listForm.setSearch("blur");
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        Page<SkillSummary> sourcePage = new Page<>();
        sourcePage.setTotalCount(1);
        sourcePage.setPagesAvailable(1);
        SkillSummary info = new SkillSummary();
        info.setName(SKILL_NAME);
        info.setDescription("desc");
        sourcePage.setPageItems(List.of(info));
        when(skillMaintainerService.listSkills(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("blur"),
            isNull(), isNull(),
            isNull(), isNull(), eq(1), eq(10))).thenReturn(sourcePage);
        
        Page<SkillSummary> result =
            skillRemoteHandler.listSkills(listForm, new AiResourceFilterableForm(), pageForm);
        
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals(SKILL_NAME, result.getPageItems().get(0).getName());
    }
    
    @Test
    void testListSkillsWithNullSource() throws NacosException {
        SkillListForm listForm = new SkillListForm();
        listForm.setNamespaceId(NAMESPACE_ID);
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        when(skillMaintainerService.listSkills(eq(NAMESPACE_ID), eq(null), eq(null), isNull(),
            isNull(), isNull(),
            isNull(), eq(1), eq(10))).thenReturn(null);
        
        Page<SkillSummary> result =
            skillRemoteHandler.listSkills(listForm, new AiResourceFilterableForm(), pageForm);
        
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getPageItems().size());
    }
    
    @Test
    void testUploadSkillFromZip() throws NacosException {
        byte[] zipBytes = "test".getBytes();
        SkillUploadRequest request = SkillUploadRequest.builder().namespaceId(NAMESPACE_ID)
            .zipBytes(zipBytes).overwrite(false).build();
        when(skillMaintainerService.uploadSkillFromZip(eq(NAMESPACE_ID), eq(zipBytes), eq(false),
            isNull(), isNull())).thenReturn(SKILL_NAME);
        
        String result = skillRemoteHandler.uploadSkillFromZip(request);
        
        assertEquals(SKILL_NAME, result);
        verify(skillMaintainerService).uploadSkillFromZip(NAMESPACE_ID, zipBytes, false, null,
            null);
    }
    
    @Test
    void testUploadSkillFromZipWithTargetVersionAndCommitMsg() throws NacosException {
        byte[] zipBytes = "test".getBytes();
        SkillUploadRequest request = SkillUploadRequest.builder().namespaceId(NAMESPACE_ID)
            .zipBytes(zipBytes).overwrite(true).targetVersion("v2").commitMsg("upload commit")
            .build();
        when(skillMaintainerService.uploadSkillFromZip(eq(NAMESPACE_ID), eq(zipBytes), eq(true),
            eq("v2"), eq("upload commit"))).thenReturn(SKILL_NAME);
        
        String result = skillRemoteHandler.uploadSkillFromZip(request);
        
        assertEquals(SKILL_NAME, result);
        verify(skillMaintainerService).uploadSkillFromZip(NAMESPACE_ID, zipBytes, true, "v2",
            "upload commit");
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        SkillDraftCreateForm form = new SkillDraftCreateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setBasedOnVersion("v1");
        form.prepareCreateDraftRequest();
        when(skillMaintainerService.createDraft(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"),
            isNull(), isNull(), isNull())).thenReturn("v2");
        
        String result = skillRemoteHandler.createDraft(form);
        
        assertEquals("v2", result);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        SkillUpdateForm form = new SkillUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillCard("{\"name\":\"test\"}");
        form.setSetAsLatest(true);
        when(skillMaintainerService.updateDraft(eq(NAMESPACE_ID), eq("{\"name\":\"test\"}"),
            eq(true),
            (String) isNull())).thenReturn(true);
        
        skillRemoteHandler.updateDraft(form);
        
        verify(skillMaintainerService).updateDraft(NAMESPACE_ID, "{\"name\":\"test\"}", true,
            (String) null);
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        when(skillMaintainerService.deleteDraft(eq(NAMESPACE_ID), eq(SKILL_NAME))).thenReturn(true);
        
        skillRemoteHandler.deleteDraft(form);
        
        verify(skillMaintainerService).deleteDraft(NAMESPACE_ID, SKILL_NAME);
    }
    
    @Test
    void testSubmit() throws NacosException {
        SkillSubmitForm form = new SkillSubmitForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        when(skillMaintainerService.submit(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1")))
            .thenReturn("pipeline-1");
        
        String result = skillRemoteHandler.submit(form);
        
        assertEquals("pipeline-1", result);
    }
    
    @Test
    void testPublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        when(skillMaintainerService.publish(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"), eq(true)))
            .thenReturn(true);
        
        skillRemoteHandler.publish(form);
        
        verify(skillMaintainerService).publish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        SkillLabelsUpdateForm form = new SkillLabelsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setLabels("{\"latest\":\"v2\"}");
        when(skillMaintainerService.updateLabels(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("{\"latest\":\"v2\"}"))).thenReturn(true);
        
        skillRemoteHandler.updateLabels(form);
        
        verify(skillMaintainerService).updateLabels(NAMESPACE_ID, SKILL_NAME,
            "{\"latest\":\"v2\"}");
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        SkillBizTagsUpdateForm form = new SkillBizTagsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setBizTags("[\"retail\"]");
        when(skillMaintainerService.updateBizTags(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("[\"retail\"]"))).thenReturn(
                true);
        
        skillRemoteHandler.updateBizTags(form);
        
        verify(skillMaintainerService).updateBizTags(NAMESPACE_ID, SKILL_NAME, "[\"retail\"]");
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        SkillOnlineForm form = new SkillOnlineForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setScope("version");
        form.setVersion("v1");
        when(skillMaintainerService.changeOnlineStatus(eq(NAMESPACE_ID), eq(SKILL_NAME),
            eq("version"), eq("v1"),
            eq(true))).thenReturn(true);
        
        skillRemoteHandler.changeOnlineStatus(form, true);
        
        verify(skillMaintainerService).changeOnlineStatus(NAMESPACE_ID, SKILL_NAME, "version", "v1",
            true);
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        SkillScopeForm form = new SkillScopeForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setScope("PUBLIC");
        when(skillMaintainerService.updateScope(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("PUBLIC")))
            .thenReturn(true);
        
        skillRemoteHandler.updateScope(form);
        
        verify(skillMaintainerService).updateScope(NAMESPACE_ID, SKILL_NAME, "PUBLIC");
    }
    
    @Test
    void testForcePublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        when(skillMaintainerService.forcePublish(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1"),
            eq(true))).thenReturn(
                true);
        
        skillRemoteHandler.forcePublish(form);
        
        verify(skillMaintainerService).forcePublish(NAMESPACE_ID, SKILL_NAME, "v1", true);
    }
    
    @Test
    void testRedraft() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        when(skillMaintainerService.redraft(eq(NAMESPACE_ID), eq(SKILL_NAME), eq("v1")))
            .thenReturn(true);
        
        skillRemoteHandler.redraft(form);
        
        verify(skillMaintainerService).redraft(NAMESPACE_ID, SKILL_NAME, "v1");
    }
}
