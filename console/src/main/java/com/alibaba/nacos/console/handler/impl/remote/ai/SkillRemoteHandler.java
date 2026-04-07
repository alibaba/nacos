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

package com.alibaba.nacos.console.handler.impl.remote.ai;

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
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Remote implementation of Skill handler.
 * 
 * <p>Calls remote Nacos server through maintainer client for Skill operations.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class SkillRemoteHandler implements SkillHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public SkillRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    @Override
    public SkillMeta getSkill(SkillForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().skill().getSkillMeta(
                form.getNamespaceId(),
                form.getSkillName()
        );
    }

    @Override
    public Skill getSkillVersion(SkillForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().skill().getSkillVersionDetail(
                form.getNamespaceId(),
                form.getSkillName(),
                form.getVersion()
        );
    }

    @Override
    public Skill downloadSkillVersion(SkillForm form) throws NacosException {
        return getSkillVersion(form);
    }
    
    @Override
    public void deleteSkill(SkillForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill().deleteSkill(
                form.getNamespaceId(),
                form.getSkillName()
        );
    }

    @Override
    public Page<SkillSummary> listSkills(SkillListForm skillListForm, AiResourceFilterableForm filterableForm,
            PageForm pageForm) throws NacosException {
        Page<SkillSummary> result = clientHolder.getAiMaintainerService().skill().listSkills(
                skillListForm.getNamespaceId(),
                skillListForm.getSkillName(),
                skillListForm.getSearch(),
                skillListForm.getOrderBy(),
                filterableForm.getOwner(),
                filterableForm.getScope(),
                pageForm.getPageNo(),
                pageForm.getPageSize()
        );
        if (result == null) {
            Page<SkillSummary> empty = new Page<>();
            empty.setTotalCount(0);
            empty.setPagesAvailable(0);
            empty.setPageNumber(pageForm.getPageNo());
            empty.setPageItems(new java.util.ArrayList<>());
            return empty;
        }
        return result;
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite) throws NacosException {
        return clientHolder.getAiMaintainerService().skill().uploadSkillFromZip(namespaceId, zipBytes, overwrite);
    }

    @Override
    public String createDraft(SkillDraftCreateForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().skill().createDraft(form.getNamespaceId(), form.getSkillName(),
                form.getBasedOnVersion(), form.getSkillCard());
    }

    @Override
    public void updateDraft(SkillUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill().updateDraft(form.getNamespaceId(), form.getSkillCard(),
                form.getSetAsLatest());
    }

    @Override
    public void deleteDraft(SkillForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill().deleteDraft(form.getNamespaceId(), form.getSkillName());
    }

    @Override
    public String submit(SkillSubmitForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().skill()
                .submit(form.getNamespaceId(), form.getSkillName(), form.getVersion());
    }

    @Override
    public void publish(SkillPublishForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill()
                .publish(form.getNamespaceId(), form.getSkillName(), form.getVersion(), form.getUpdateLatestLabel());
    }

    @Override
    public void forcePublish(SkillPublishForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill()
                .forcePublish(form.getNamespaceId(), form.getSkillName(), form.getVersion(), form.getUpdateLatestLabel());
    }

    @Override
    public void updateLabels(SkillLabelsUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill()
                .updateLabels(form.getNamespaceId(), form.getSkillName(), form.getLabels());
    }

    @Override
    public void updateBizTags(SkillBizTagsUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill()
                .updateBizTags(form.getNamespaceId(), form.getSkillName(), form.getBizTags());
    }

    @Override
    public void changeOnlineStatus(SkillOnlineForm form, boolean online) throws NacosException {
        clientHolder.getAiMaintainerService().skill().changeOnlineStatus(form.getNamespaceId(),
                form.getSkillName(), form.getScope(), form.getVersion(), online);
    }
    
    @Override
    public void updateScope(SkillScopeForm form) throws NacosException {
        clientHolder.getAiMaintainerService().skill().updateScope(form.getNamespaceId(), form.getSkillName(),
                form.getScope());
    }
}
