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

import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
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
    public Skill getSkill(SkillForm form) throws NacosException {
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
    public Page<SkillBasicInfo> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException {
        return clientHolder.getAiMaintainerService().listSkills(
                skillListForm.getNamespaceId(),
                skillListForm.getSkillName(),
                skillListForm.getSearch(),
                pageForm.getPageNo(),
                pageForm.getPageSize()
        );
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return clientHolder.getAiMaintainerService().uploadSkillFromZip(namespaceId, zipBytes);
    }
}
