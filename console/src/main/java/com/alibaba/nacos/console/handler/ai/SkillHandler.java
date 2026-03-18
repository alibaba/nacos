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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;

/**
 * Skill handler.
 *
 * @author nacos
 */
public interface SkillHandler {
    
    /**
     * Get skill.
     *
     * @param form skill form
     * @return skill
     * @throws NacosException nacos exception
     */
    Skill getSkill(SkillForm form) throws NacosException;
    
    /**
     * Delete skill.
     *
     * @param form skill form
     * @throws NacosException nacos exception
     */
    void deleteSkill(SkillForm form) throws NacosException;
    
    /**
     * List skills.
     *
     * @param skillListForm skill list form
     * @param pageForm page form
     * @return skill list
     * @throws NacosException nacos exception
     */
    Page<SkillBasicInfo> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException;
    
    /**
     * Upload skill from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @return skill name
     * @throws NacosException if upload failed
     */
    String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException;
}
