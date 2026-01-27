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

import com.alibaba.nacos.ai.form.skills.admin.SkillDetailForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

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
    public void registerSkill(Skill skill, SkillDetailForm form) throws NacosException {
        skillOperationService.registerSkill(skill, form.getNamespaceId());
    }
    
    @Override
    public Skill getSkill(SkillForm form) throws NacosException {
        return skillOperationService.getSkillDetail(form.getNamespaceId(), form.getSkillName());
    }
    
    @Override
    public void deleteSkill(SkillForm form) throws NacosException {
        skillOperationService.deleteSkill(form.getNamespaceId(), form.getSkillName());
    }
    
    @Override
    public void updateSkill(Skill skill, SkillUpdateForm form) throws NacosException {
        skillOperationService.updateSkill(skill, form.getNamespaceId());
    }
    
    @Override
    public Page<SkillBasicInfo> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException {
        return skillOperationService.listSkills(skillListForm.getNamespaceId(), skillListForm.getSkillName(),
                skillListForm.getSearch(), pageForm.getPageNo(), pageForm.getPageSize());
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return skillOperationService.uploadSkillFromZip(namespaceId, zipBytes);
    }
}
