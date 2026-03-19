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

import com.alibaba.nacos.ai.form.skills.admin.SkillDraftCreateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillLabelsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillOnlineForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillSubmitForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.ai.model.skills.SkillAdminDetail;
import com.alibaba.nacos.ai.model.skills.SkillAdminListItem;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
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
    public SkillAdminDetail getSkill(SkillForm form) throws NacosException {
        // Remote maintainer client currently does not support full admin detail;
        // return empty detail as placeholder.
        return new SkillAdminDetail();
    }

    @Override
    public Skill getSkillVersion(SkillForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().getSkillDetail(
                form.getNamespaceId(),
                form.getSkillName()
        );
    }
    
    @Override
    public void deleteSkill(SkillForm form) throws NacosException {
        clientHolder.getAiMaintainerService().deleteSkill(
                form.getNamespaceId(),
                form.getSkillName()
        );
    }

    @Override
    public Page<SkillAdminListItem> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException {
        // Remote maintainer client returns Page<SkillBasicInfo>; convert to Page<SkillAdminListItem>
        Page<SkillBasicInfo> source = clientHolder.getAiMaintainerService().listSkills(
                skillListForm.getNamespaceId(),
                skillListForm.getSkillName(),
                skillListForm.getSearch(),
                pageForm.getPageNo(),
                pageForm.getPageSize()
        );
        Page<SkillAdminListItem> result = new Page<>();
        result.setTotalCount(source == null ? 0 : source.getTotalCount());
        result.setPagesAvailable(source == null ? 0 : source.getPagesAvailable());
        result.setPageNumber(pageForm.getPageNo());
        java.util.List<SkillAdminListItem> items = new java.util.ArrayList<>();
        if (source != null && source.getPageItems() != null) {
            for (SkillBasicInfo info : source.getPageItems()) {
                if (info == null) {
                    continue;
                }
                SkillAdminListItem item = new SkillAdminListItem();
                item.setName(info.getName());
                item.setDescription(info.getDescription());
                items.add(item);
            }
        }
        result.setPageItems(items);
        return result;
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return clientHolder.getAiMaintainerService().uploadSkillFromZip(namespaceId, zipBytes);
    }

    @Override
    public String createDraft(SkillDraftCreateForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().createDraft(form.getNamespaceId(), form.getSkillName(),
                form.getBasedOnVersion());
    }

    @Override
    public void updateDraft(SkillUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().updateDraft(form.getNamespaceId(), form.getSkillCard(),
                form.getSetAsLatest());
    }

    @Override
    public void deleteDraft(SkillForm form) throws NacosException {
        clientHolder.getAiMaintainerService().deleteDraft(form.getNamespaceId(), form.getSkillName());
    }

    @Override
    public String submit(SkillSubmitForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().submit(form.getNamespaceId(), form.getSkillName(), form.getVersion());
    }

    @Override
    public void publish(SkillPublishForm form) throws NacosException {
        clientHolder.getAiMaintainerService().publish(form.getNamespaceId(), form.getSkillName(), form.getVersion(),
                form.getUpdateLatestLabel());
    }

    @Override
    public void updateLabels(SkillLabelsUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().updateLabels(form.getNamespaceId(), form.getSkillName(), form.getLabels());
    }

    @Override
    public void changeOnlineStatus(SkillOnlineForm form, boolean online) throws NacosException {
        clientHolder.getAiMaintainerService().changeOnlineStatus(form.getNamespaceId(), form.getSkillName(),
                form.getScope(), form.getVersion(), online);
    }
}
