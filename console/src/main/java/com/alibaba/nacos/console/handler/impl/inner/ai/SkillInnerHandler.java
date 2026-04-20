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
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.ai.utils.SkillRequestUtil;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Skill inner handler.
 *
 * @author nacos
 */
@Component
@EnabledInnerHandler
@EnabledAiHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class SkillInnerHandler implements SkillHandler {
    
    private final SkillOperationService skillOperationService;
    
    public SkillInnerHandler(SkillOperationService skillOperationService) {
        this.skillOperationService = skillOperationService;
    }
    
    @Override
    public SkillMeta getSkill(SkillForm form) throws NacosException {
        return skillOperationService.getSkillDetail(form.getNamespaceId(), form.getSkillName());
    }

    @Override
    public Skill getSkillVersion(SkillForm form) throws NacosException {
        return skillOperationService.getSkillVersionDetail(form.getNamespaceId(), form.getSkillName(), form.getVersion());
    }

    @Override
    public Skill downloadSkillVersion(SkillForm form) throws NacosException {
        return skillOperationService.downloadSkillVersion(form.getNamespaceId(), form.getSkillName(), form.getVersion());
    }
    
    @Override
    public void deleteSkill(SkillForm form) throws NacosException {
        skillOperationService.deleteSkill(form.getNamespaceId(), form.getSkillName());
    }
    
    @Override
    public Page<SkillSummary> listSkills(SkillListForm skillListForm, AiResourceFilterableForm filterableForm,
            PageForm pageForm) throws NacosException {
        return skillOperationService.listSkills(skillListForm.getNamespaceId(), skillListForm.getSkillName(),
                skillListForm.getSearch(), skillListForm.getOrderBy(),
                filterableForm.getOwner(), filterableForm.getScope(), filterableForm.getBizTag(),
                pageForm.getPageNo(), pageForm.getPageSize());
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite) throws NacosException {
        return skillOperationService.uploadSkillFromZip(namespaceId, zipBytes, overwrite);
    }

    @Override
    public String createDraft(SkillDraftCreateForm form) throws NacosException {
        return skillOperationService.createDraft(form.getNamespaceId(), form.getSkillName(), form.getBasedOnVersion(),
                form.getTargetVersion(), form.getResolvedInitialSkillOrNull(), form.getCommitMsg());
    }

    @Override
    public void updateDraft(SkillUpdateForm form) throws NacosException {
        Skill skill = SkillRequestUtil.parseSkill(form);
        skillOperationService.updateDraft(form.getNamespaceId(), skill, form.getCommitMsg());
    }

    @Override
    public void deleteDraft(SkillForm form) throws NacosException {
        skillOperationService.deleteDraft(form.getNamespaceId(), form.getSkillName());
    }

    @Override
    public String submit(SkillSubmitForm form) throws NacosException {
        return skillOperationService.submit(form.getNamespaceId(), form.getSkillName(), form.getVersion());
    }

    @Override
    public void publish(SkillPublishForm form) throws NacosException {
        boolean updateLatest = form.getUpdateLatestLabel() == null || form.getUpdateLatestLabel();
        skillOperationService.publish(form.getNamespaceId(), form.getSkillName(), form.getVersion(), updateLatest);
    }

    @Override
    public void forcePublish(SkillPublishForm form) throws NacosException {
        boolean updateLatest = form.getUpdateLatestLabel() == null || form.getUpdateLatestLabel();
        skillOperationService.forcePublish(form.getNamespaceId(), form.getSkillName(), form.getVersion(), updateLatest);
    }

    @Override
    public void updateLabels(SkillLabelsUpdateForm form) throws NacosException {
        Map<String, String> labels = JacksonUtils.toObj(form.getLabels(), Map.class);
        skillOperationService.updateLabels(form.getNamespaceId(), form.getSkillName(), labels);
    }

    @Override
    public void updateBizTags(SkillBizTagsUpdateForm form) throws NacosException {
        skillOperationService.updateBizTags(form.getNamespaceId(), form.getSkillName(), form.getBizTags());
    }

    @Override
    public void changeOnlineStatus(SkillOnlineForm form, boolean online) throws NacosException {
        skillOperationService.changeOnlineStatus(form.getNamespaceId(), form.getSkillName(), form.getScope(),
                form.getVersion(), online);
    }
    
    @Override
    public void updateScope(SkillScopeForm form) throws NacosException {
        skillOperationService.updateScope(form.getNamespaceId(), form.getSkillName(), form.getScope());
    }
}
