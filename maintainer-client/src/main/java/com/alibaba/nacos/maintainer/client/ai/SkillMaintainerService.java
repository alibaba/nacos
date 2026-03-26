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
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
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
     * Get skill metadata with default namespace.
     *
     * @param skillName skill name
     * @return skill metadata
     * @throws NacosException if fail to get skill metadata
     */
    default SkillMeta getSkillMeta(String skillName) throws NacosException {
        return getSkillMeta(Constants.DEFAULT_NAMESPACE_ID, skillName);
    }
    
    /**
     * Get skill metadata.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @return skill metadata
     * @throws NacosException if fail to get skill metadata
     */
    SkillMeta getSkillMeta(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Get specific skill version detail with default namespace.
     *
     * @param skillName skill name
     * @param version   skill version
     * @return skill version detail
     * @throws NacosException if fail to get skill version detail
     */
    default Skill getSkillVersionDetail(String skillName, String version) throws NacosException {
        return getSkillVersionDetail(Constants.DEFAULT_NAMESPACE_ID, skillName, version);
    }
    
    /**
     * Get specific skill version detail.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param version     skill version
     * @return skill version detail
     * @throws NacosException if fail to get skill version detail
     */
    Skill getSkillVersionDetail(String namespaceId, String skillName, String version) throws NacosException;
    
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
     * Delete skill.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @return true if delete success
     * @throws NacosException if fail to delete skill
     */
    boolean deleteSkill(String namespaceId, String skillName) throws NacosException;
    
    /**
     * List skills with default namespace.
     *
     * @param skillName skill name pattern for filtering
     * @param pageNo    page number
     * @param pageSize  page size
     * @return paged skill list
     * @throws NacosException if fail to list skills
     */
    default Page<SkillSummary> listSkills(String skillName, int pageNo, int pageSize) throws NacosException {
        return listSkills(Constants.DEFAULT_NAMESPACE_ID, skillName, "blur", pageNo, pageSize);
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
    Page<SkillSummary> listSkills(String namespaceId, String skillName, String search, int pageNo, int pageSize)
            throws NacosException;

    /**
     * Upload skill from zip file with default namespace.
     *
     * @param zipBytes zip file bytes
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    default String uploadSkillFromZip(byte[] zipBytes) throws NacosException {
        return uploadSkillFromZip(Constants.DEFAULT_NAMESPACE_ID, zipBytes, false);
    }
    
    /**
     * Upload skill from zip file without overwrite.
     *
     * @param namespaceId namespace ID
     * @param zipBytes    zip file bytes
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, false);
    }
    
    /**
     * Upload skill from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @param overwrite whether to overwrite the current editable draft when the skill already exists
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite) throws NacosException;
    
    /**
     * Create a brand-new skill draft.
     *
     * @param namespaceId namespace ID
     * @param skillCard   skill card JSON string
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    default String createDraft(String namespaceId, String skillCard) throws NacosException {
        return createDraft(namespaceId, null, null, null, skillCard);
    }
    
    /**
     * Create draft from an existed version (Forking).
     *
     * @param namespaceId    namespace ID
     * @param skillName      skill name
     * @param basedOnVersion base version to fork from
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    default String createDraft(String namespaceId, String skillName, String basedOnVersion) throws NacosException {
        return createDraft(namespaceId, skillName, basedOnVersion, null, null);
    }
    
    /**
     * Create draft version for a skill with optional target version.
     *
     * @param namespaceId    namespace ID
     * @param skillName      skill name (required when forking)
     * @param basedOnVersion base version to fork from (optional)
     * @param targetVersion  target draft version to create (optional)
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    default String createDraft(String namespaceId, String skillName, String basedOnVersion, String targetVersion)
            throws NacosException {
        return createDraft(namespaceId, skillName, basedOnVersion, targetVersion, null);
    }
    
    /**
     * Create draft version for a skill.
     * {@code skillCard} is required unless forking ({@code basedOnVersion} set); same JSON as update draft.
     *
     * @param namespaceId    namespace ID
     * @param skillName      skill name (required when forking)
     * @param basedOnVersion base version to fork from (optional)
     * @param targetVersion  target draft version to create (optional)
     * @param skillCard      full skill JSON, or null when forking
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    String createDraft(String namespaceId, String skillName, String basedOnVersion, String targetVersion,
            String skillCard)
            throws NacosException;
    
    /**
     * Update current draft content.
     *
     * @param namespaceId namespace ID
     * @param skillCard   skill card JSON string
     * @param setAsLatest whether set as latest (optional)
     * @return true if update success
     * @throws NacosException if fail to update draft
     */
    boolean updateDraft(String namespaceId, String skillCard, Boolean setAsLatest) throws NacosException;
    
    /**
     * Delete current draft version.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @return true if delete success
     * @throws NacosException if fail to delete draft
     */
    boolean deleteDraft(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Submit a version for pipeline review.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param version     version (optional, server may choose current editing)
     * @return submit result (e.g. pipeline id)
     * @throws NacosException if fail to submit
     */
    String submit(String namespaceId, String skillName, String version) throws NacosException;
    
    /**
     * Publish an approved reviewing version.
     *
     * @param namespaceId       namespace ID
     * @param skillName         skill name
     * @param version           version
     * @param updateLatestLabel update latest label, default true if null
     * @return true if publish success
     * @throws NacosException if fail to publish
     */
    boolean publish(String namespaceId, String skillName, String version, Boolean updateLatestLabel)
            throws NacosException;
    
    /**
     * Update runtime labels mapping JSON.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param labels      JSON string
     * @return true if update success
     * @throws NacosException if fail to update labels
     */
    boolean updateLabels(String namespaceId, String skillName, String labels) throws NacosException;

    /**
     * Update skill biz tags JSON.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @param bizTags biz tags JSON string
     * @return true if update success
     * @throws NacosException if fail to update biz tags
     */
    boolean updateBizTags(String namespaceId, String skillName, String bizTags) throws NacosException;
    
    /**
     * Online/offline operation.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param scope       "skill" for skill-level enable/disable; otherwise version-level
     * @param version     version for version-level (optional)
     * @param online      true for online(enable), false for offline(disable)
     * @return true if operation success
     * @throws NacosException if fail to change status
     */
    boolean changeOnlineStatus(String namespaceId, String skillName, String scope, String version, boolean online)
            throws NacosException;
    
    /**
     * Update skill visibility scope.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param scope       scope value, e.g. PUBLIC/PRIVATE
     * @return true if update success
     * @throws NacosException if fail to update scope
     */
    boolean updateScope(String namespaceId, String skillName, String scope) throws NacosException;
}
