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

import com.alibaba.nacos.ai.form.skills.admin.SkillDetailForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
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
    
    /**
     * Register skill.
     *
     * @param skill skill to register
     * @param form skill detail form
     * @throws NacosException exception when register skill
     */
    public void registerSkill(Skill skill, SkillDetailForm form) throws NacosException {
        skillHandler.registerSkill(skill, form);
    }
    
    public Skill getSkill(SkillForm form) throws NacosException {
        return skillHandler.getSkill(form);
    }
    
    public void deleteSkill(SkillForm form) throws NacosException {
        skillHandler.deleteSkill(form);
    }
    
    public void updateSkill(Skill skill, SkillUpdateForm form) throws NacosException {
        skillHandler.updateSkill(skill, form);
    }
    
    public Page<SkillBasicInfo> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException {
        return skillHandler.listSkills(skillListForm, pageForm);
    }
    
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return skillHandler.uploadSkillFromZip(namespaceId, zipBytes);
    }
}
