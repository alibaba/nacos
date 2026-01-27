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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Skill operation service.
 *
 * @author nacos
 */
public interface SkillOperationService {
    
    /**
     * Register a new skill.
     *
     * @param skill skill object
     * @param namespaceId namespace ID
     * @return skill ID
     * @throws NacosException if registration failed
     */
    String registerSkill(Skill skill, String namespaceId) throws NacosException;
    
    /**
     * Get skill detail.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @return skill detail
     * @throws NacosException if skill not found
     */
    Skill getSkillDetail(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Update skill.
     *
     * @param skill skill object
     * @param namespaceId namespace ID
     * @throws NacosException if update failed
     */
    void updateSkill(Skill skill, String namespaceId) throws NacosException;
    
    /**
     * Delete skill.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @throws NacosException if delete failed
     */
    void deleteSkill(String namespaceId, String skillName) throws NacosException;
    
    /**
     * List skills with pagination.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name (for search)
     * @param search search keyword
     * @param pageNo page number
     * @param pageSize page size
     * @return skill list page
     * @throws NacosException if query failed
     */
    Page<SkillBasicInfo> listSkills(String namespaceId, String skillName, String search, int pageNo, int pageSize) throws NacosException;
    
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
