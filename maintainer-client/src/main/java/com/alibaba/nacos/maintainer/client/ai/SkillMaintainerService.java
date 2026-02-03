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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Nacos AI module Skill relative maintainer service.
 *
 * @author nacos
 */
public interface SkillMaintainerService {
    
    /**
     * Register a new skill.
     *
     * @param namespaceId namespace ID
     * @param skill       skill object to register
     * @return skill name
     * @throws NacosException if fail to register skill
     */
    String registerSkill(String namespaceId, Skill skill) throws NacosException;
    
    /**
     * Register skill with default namespace.
     *
     * @param skill skill object to register
     * @return skill name
     * @throws NacosException if fail to register skill
     */
    default String registerSkill(Skill skill) throws NacosException {
        return registerSkill(Constants.DEFAULT_NAMESPACE_ID, skill);
    }
    
    /**
     * Get skill detail.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @return skill detail
     * @throws NacosException if fail to get skill
     */
    Skill getSkillDetail(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Get skill detail with default namespace.
     *
     * @param skillName skill name
     * @return skill detail
     * @throws NacosException if fail to get skill
     */
    default Skill getSkillDetail(String skillName) throws NacosException {
        return getSkillDetail(Constants.DEFAULT_NAMESPACE_ID, skillName);
    }
    
    /**
     * Update skill.
     *
     * @param namespaceId namespace ID
     * @param skill       skill object to update
     * @return true if update success
     * @throws NacosException if fail to update skill
     */
    boolean updateSkill(String namespaceId, Skill skill) throws NacosException;
    
    /**
     * Update skill with default namespace.
     *
     * @param skill skill object to update
     * @return true if update success
     * @throws NacosException if fail to update skill
     */
    default boolean updateSkill(Skill skill) throws NacosException {
        return updateSkill(Constants.DEFAULT_NAMESPACE_ID, skill);
    }
    
    /**
     * Delete skill.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @return true if delete success
     * @throws NacosException if fail to delete skill
     */
    boolean deleteSkill(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Delete skill with default namespace.
     *
     * @param skillName skill name
     * @return true if delete success
     * @throws NacosException if fail to delete skill
     */
    default boolean deleteSkill(String skillName) throws NacosException {
        return deleteSkill(Constants.DEFAULT_NAMESPACE_ID, skillName);
    }
    
    /**
     * List skills with pagination.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name pattern for filtering
     * @param search      search mode: "accurate" or "blur"
     * @param pageNo      page number
     * @param pageSize    page size
     * @return paged skill list
     * @throws NacosException if fail to list skills
     */
    Page<SkillBasicInfo> listSkills(String namespaceId, String skillName, String search, int pageNo, int pageSize)
            throws NacosException;
    
    /**
     * List skills with default namespace.
     *
     * @param skillName skill name pattern for filtering
     * @param pageNo    page number
     * @param pageSize  page size
     * @return paged skill list
     * @throws NacosException if fail to list skills
     */
    default Page<SkillBasicInfo> listSkills(String skillName, int pageNo, int pageSize) throws NacosException {
        return listSkills(Constants.DEFAULT_NAMESPACE_ID, skillName, "blur", pageNo, pageSize);
    }
    
    /**
     * Upload skill from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes    zip file bytes
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException;
    
    /**
     * Upload skill from zip file with default namespace.
     *
     * @param zipBytes zip file bytes
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    default String uploadSkillFromZip(byte[] zipBytes) throws NacosException {
        return uploadSkillFromZip(Constants.DEFAULT_NAMESPACE_ID, zipBytes);
    }
}
