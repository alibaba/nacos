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

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillDraftCreateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillLabelsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillOnlineForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillScopeForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillSubmitForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.stereotype.Component;

/**
 * Skill proxy.
 *
 * @author nacos
 */
@Component
public class SkillProxy {
    
    private final SkillHandler skillHandler;
    
    public SkillProxy(SkillHandler skillHandler) {
        this.skillHandler = skillHandler;
    }
    
    public SkillMeta getSkill(SkillForm form) throws NacosException {
        return skillHandler.getSkill(form);
    }
    
    public Skill getSkillVersion(SkillForm form) throws NacosException {
        return skillHandler.getSkillVersion(form);
    }
    
    public Skill downloadSkillVersion(SkillForm form) throws NacosException {
        return skillHandler.downloadSkillVersion(form);
    }
    
    public void deleteSkill(SkillForm form) throws NacosException {
        skillHandler.deleteSkill(form);
    }
    
    public Page<SkillSummary> listSkills(SkillListForm skillListForm,
        AiResourceFilterableForm filterableForm,
        PageForm pageForm) throws NacosException {
        return skillHandler.listSkills(skillListForm, filterableForm, pageForm);
    }
    
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, false, null);
    }
    
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite, null);
    }
    
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion)
        throws NacosException {
        return skillHandler.uploadSkillFromZip(namespaceId, zipBytes, overwrite, targetVersion);
    }
    
    public BatchUploadResult batchUploadSkillsFromZip(String namespaceId, byte[] zipBytes,
        boolean overwrite)
        throws NacosException {
        return skillHandler.batchUploadSkillsFromZip(namespaceId, zipBytes, overwrite);
    }
    
    public String createDraft(SkillDraftCreateForm form) throws NacosException {
        return skillHandler.createDraft(form);
    }
    
    public void updateDraft(SkillUpdateForm form) throws NacosException {
        skillHandler.updateDraft(form);
    }
    
    public void deleteDraft(SkillForm form) throws NacosException {
        skillHandler.deleteDraft(form);
    }
    
    public String submit(SkillSubmitForm form) throws NacosException {
        return skillHandler.submit(form);
    }
    
    public void publish(SkillPublishForm form) throws NacosException {
        skillHandler.publish(form);
    }
    
    public void forcePublish(SkillPublishForm form) throws NacosException {
        skillHandler.forcePublish(form);
    }
    
    public void redraft(SkillPublishForm form) throws NacosException {
        skillHandler.redraft(form);
    }
    
    public void updateLabels(SkillLabelsUpdateForm form) throws NacosException {
        skillHandler.updateLabels(form);
    }
    
    public void updateBizTags(SkillBizTagsUpdateForm form) throws NacosException {
        skillHandler.updateBizTags(form);
    }
    
    public void online(SkillOnlineForm form) throws NacosException {
        skillHandler.changeOnlineStatus(form, true);
    }
    
    public void offline(SkillOnlineForm form) throws NacosException {
        skillHandler.changeOnlineStatus(form, false);
    }
    
    public void updateScope(SkillScopeForm form) throws NacosException {
        skillHandler.updateScope(form);
    }
}
