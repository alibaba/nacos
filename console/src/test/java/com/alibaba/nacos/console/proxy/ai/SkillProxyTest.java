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
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillProxyTest {
    
    private static final String NS = "test-ns";
    
    private static final String SKILL_NAME = "test-skill";
    
    @Mock
    private SkillHandler skillHandler;
    
    private SkillProxy skillProxy;
    
    @BeforeEach
    void setUp() {
        skillProxy = new SkillProxy(skillHandler);
    }
    
    @Test
    void testGetSkill() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        SkillMeta meta = new SkillMeta();
        meta.setName(SKILL_NAME);
        when(skillHandler.getSkill(form)).thenReturn(meta);
        
        SkillMeta result = skillProxy.getSkill(form);
        
        assertEquals(SKILL_NAME, result.getName());
        verify(skillHandler).getSkill(form);
    }
    
    @Test
    void testGetSkillVersion() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillHandler.getSkillVersion(form)).thenReturn(skill);
        
        Skill result = skillProxy.getSkillVersion(form);
        
        assertEquals(SKILL_NAME, result.getName());
        verify(skillHandler).getSkillVersion(form);
    }
    
    @Test
    void testDownloadSkillVersion() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillHandler.downloadSkillVersion(form)).thenReturn(skill);
        
        Skill result = skillProxy.downloadSkillVersion(form);
        
        assertNotNull(result);
        verify(skillHandler).downloadSkillVersion(form);
    }
    
    @Test
    void testDeleteSkill() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillHandler).deleteSkill(form);
        
        skillProxy.deleteSkill(form);
        
        verify(skillHandler).deleteSkill(form);
    }
    
    @Test
    void testListSkills() throws NacosException {
        SkillListForm listForm = new SkillListForm();
        AiResourceFilterableForm filterForm = new AiResourceFilterableForm();
        PageForm pageForm = new PageForm();
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(2);
        page.setPageItems(List.of(new SkillSummary(), new SkillSummary()));
        when(skillHandler.listSkills(listForm, filterForm, pageForm)).thenReturn(page);
        
        Page<SkillSummary> result = skillProxy.listSkills(listForm, filterForm, pageForm);
        
        assertEquals(2, result.getTotalCount());
        verify(skillHandler).listSkills(listForm, filterForm, pageForm);
    }
    
    @Test
    void testUploadSkillFromZip() throws NacosException {
        byte[] zipBytes = new byte[] {1, 2, 3};
        SkillUploadRequest request = SkillUploadRequest.builder()
            .namespaceId(NS)
            .zipBytes(zipBytes)
            .overwrite(true)
            .targetVersion("v2")
            .commitMsg("upload commit")
            .build();
        when(skillHandler.uploadSkillFromZip(request)).thenReturn(SKILL_NAME);
        
        String result = skillProxy.uploadSkillFromZip(request);
        
        assertEquals(SKILL_NAME, result);
        verify(skillHandler).uploadSkillFromZip(request);
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        SkillDraftCreateForm form = new SkillDraftCreateForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        when(skillHandler.createDraft(form)).thenReturn("v1-draft");
        
        String result = skillProxy.createDraft(form);
        
        assertEquals("v1-draft", result);
        verify(skillHandler).createDraft(form);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        SkillUpdateForm form = new SkillUpdateForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillHandler).updateDraft(form);
        
        skillProxy.updateDraft(form);
        
        verify(skillHandler).updateDraft(form);
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        SkillForm form = new SkillForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillHandler).deleteDraft(form);
        
        skillProxy.deleteDraft(form);
        
        verify(skillHandler).deleteDraft(form);
    }
    
    @Test
    void testSubmit() throws NacosException {
        SkillSubmitForm form = new SkillSubmitForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        when(skillHandler.submit(form)).thenReturn("reviewing");
        
        String result = skillProxy.submit(form);
        
        assertEquals("reviewing", result);
        verify(skillHandler).submit(form);
    }
    
    @Test
    void testPublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        doNothing().when(skillHandler).publish(form);
        
        skillProxy.publish(form);
        
        verify(skillHandler).publish(form);
    }
    
    @Test
    void testForcePublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        doNothing().when(skillHandler).forcePublish(form);
        
        skillProxy.forcePublish(form);
        
        verify(skillHandler).forcePublish(form);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        SkillLabelsUpdateForm form = new SkillLabelsUpdateForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillHandler).updateLabels(form);
        
        skillProxy.updateLabels(form);
        
        verify(skillHandler).updateLabels(form);
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        SkillBizTagsUpdateForm form = new SkillBizTagsUpdateForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillHandler).updateBizTags(form);
        
        skillProxy.updateBizTags(form);
        
        verify(skillHandler).updateBizTags(form);
    }
    
    @Test
    void testOnline() throws NacosException {
        SkillOnlineForm form = new SkillOnlineForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        doNothing().when(skillHandler).changeOnlineStatus(form, true);
        
        skillProxy.online(form);
        
        verify(skillHandler).changeOnlineStatus(form, true);
    }
    
    @Test
    public void testRedraft() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        
        doNothing().when(skillHandler).redraft(form);
        
        skillProxy.redraft(form);
        
        verify(skillHandler, times(1)).redraft(form);
    }
    
    @Test
    void testOffline() throws NacosException {
        SkillOnlineForm form = new SkillOnlineForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        doNothing().when(skillHandler).changeOnlineStatus(form, false);
        
        skillProxy.offline(form);
        
        verify(skillHandler).changeOnlineStatus(form, false);
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        SkillScopeForm form = new SkillScopeForm();
        form.setNamespaceId(NS);
        form.setSkillName(SKILL_NAME);
        doNothing().when(skillHandler).updateScope(form);
        
        skillProxy.updateScope(form);
        
        verify(skillHandler).updateScope(form);
    }
}
