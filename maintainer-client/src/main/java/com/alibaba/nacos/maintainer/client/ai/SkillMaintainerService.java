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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
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
    @Since("3.2.0")
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
    @Since("3.2.0")
    SkillMeta getSkillMeta(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Get specific skill version detail with default namespace.
     *
     * @param skillName skill name
     * @param version   skill version
     * @return skill version detail
     * @throws NacosException if fail to get skill version detail
     */
    @Since("3.2.0")
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
    @Since("3.2.0")
    Skill getSkillVersionDetail(String namespaceId, String skillName, String version)
        throws NacosException;
    
    /**
     * Delete skill with default namespace.
     *
     * @param skillName skill name
     * @return true if delete success
     * @throws NacosException if fail to delete skill
     */
    @Since("3.2.0")
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
    @Since("3.2.0")
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
    @Since("3.2.0")
    default Page<SkillSummary> listSkills(String skillName, int pageNo, int pageSize)
        throws NacosException {
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
    @Since("3.2.0")
    Page<SkillSummary> listSkills(String namespaceId, String skillName, String search, int pageNo,
        int pageSize)
        throws NacosException;
    
    /**
     * List skills with pagination, optional ordering and additional filter criteria.
     *
     * <p>Backward-compatible: when {@code orderBy}, {@code owner} and {@code scope} are all {@code null}/empty,
     * the behaviour is identical to {@link #listSkills(String, String, String, int, int)}.</p>
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name pattern for filtering
     * @param search      search mode: "accurate" or "blur"
     * @param orderBy     optional sort field (e.g. "download_count"); null defaults to gmt_modified
     * @param owner       optional filter by resource owner; null or empty means no owner filter
     * @param scope       optional filter by visibility scope ("PUBLIC"/"PRIVATE"); null or empty means no scope filter
     * @param pageNo      page number
     * @param pageSize    page size
     * @return paged skill list
     * @throws NacosException if fail to list skills
     */
    @Since("3.2.1")
    default Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        String owner, String scope, int pageNo, int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, orderBy, owner, scope, null, pageNo,
            pageSize);
    }
    
    /**
     * List skills with pagination, optional ordering and additional filter criteria including bizTag.
     *
     * <p>Backward-compatible: when {@code orderBy}, {@code owner}, {@code scope} and {@code bizTag}
     * are all {@code null}/empty, the behaviour is identical to
     * {@link #listSkills(String, String, String, int, int)}.</p>
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name pattern for filtering
     * @param search      search mode: "accurate" or "blur"
     * @param orderBy     optional sort field (e.g. "download_count"); null defaults to gmt_modified
     * @param owner       optional filter by resource owner; null or empty means no owner filter
     * @param scope       optional filter by visibility scope ("PUBLIC"/"PRIVATE"); null or empty means no scope filter
     * @param bizTag      optional filter by business tag (fuzzy match); null or empty means no bizTag filter
     * @param pageNo      page number
     * @param pageSize    page size
     * @return paged skill list
     * @throws NacosException if fail to list skills
     */
    @Since("3.2.1")
    default Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        String owner, String scope, String bizTag, int pageNo, int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, pageNo, pageSize);
    }
    
    /**
     * Upload skill from zip file with default namespace.
     *
     * @param zipBytes zip file bytes
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    @Since("3.2.0")
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
    @Since("3.2.0")
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
    @Since("3.2.0")
    String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException;
    
    /**
     * Upload skill from zip file with optional target version.
     *
     * @param namespaceId   namespace ID
     * @param zipBytes      zip file bytes
     * @param overwrite     whether to overwrite the current editable draft when the skill already exists
     * @param targetVersion user-specified version (optional, used as fallback when ZIP content has no version)
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    @Since("3.2.2")
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite, targetVersion, null);
    }
    
    /**
     * Upload skill from zip file with optional target version and commit message.
     *
     * @param namespaceId   namespace ID
     * @param zipBytes      zip file bytes
     * @param overwrite     whether to overwrite the current editable draft when the skill already exists
     * @param targetVersion user-specified version (optional, used as fallback when ZIP content has no version)
     * @param commitMsg     version-level commit message (optional)
     * @return skill name
     * @throws NacosException if fail to upload skill
     */
    @Since("3.2.2")
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion, String commitMsg)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite);
    }
    
    /**
     * Batch upload skills from a multi-skill zip archive with default namespace.
     *
     * @param zipBytes zip file bytes containing multiple skill directories
     * @return batch upload result with succeeded and failed lists
     * @throws NacosException if fail to upload
     */
    @Since("3.2.2")
    default BatchUploadResult batchUploadSkillsFromZip(byte[] zipBytes) throws NacosException {
        return batchUploadSkillsFromZip(Constants.DEFAULT_NAMESPACE_ID, zipBytes, false);
    }
    
    /**
     * Batch upload skills from a multi-skill zip archive.
     *
     * @param namespaceId namespace ID
     * @param zipBytes    zip file bytes containing multiple skill directories
     * @param overwrite   whether to overwrite existing drafts
     * @return batch upload result with succeeded and failed lists
     * @throws NacosException if fail to upload
     */
    @Since("3.2.2")
    BatchUploadResult batchUploadSkillsFromZip(String namespaceId, byte[] zipBytes,
        boolean overwrite)
        throws NacosException;
    
    /**
     * Create a brand-new skill draft.
     *
     * @param namespaceId namespace ID
     * @param skillCard   skill card JSON string
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    @Since("3.2.0")
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
    @Since("3.2.0")
    default String createDraft(String namespaceId, String skillName, String basedOnVersion)
        throws NacosException {
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
    @Since("3.2.0")
    default String createDraft(String namespaceId, String skillName, String basedOnVersion,
        String targetVersion)
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
    @Since("3.2.0")
    default String createDraft(String namespaceId, String skillName, String basedOnVersion,
        String targetVersion,
        String skillCard) throws NacosException {
        return createDraft(namespaceId, skillName, basedOnVersion, targetVersion, skillCard, null);
    }
    
    /**
     * Create draft version for a skill with optional commit message.
     *
     * @param namespaceId    namespace ID
     * @param skillName      skill name (required when forking)
     * @param basedOnVersion base version to fork from (optional)
     * @param targetVersion  target draft version to create (optional)
     * @param skillCard      full skill JSON, or null when forking
     * @param commitMsg      version-level commit message (optional)
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    @Since("3.2.1")
    String createDraft(String namespaceId, String skillName, String basedOnVersion,
        String targetVersion,
        String skillCard, String commitMsg)
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
    @Since("3.2.0")
    default boolean updateDraft(String namespaceId, String skillCard, Boolean setAsLatest)
        throws NacosException {
        return updateDraft(namespaceId, skillCard, setAsLatest, null);
    }
    
    /**
     * Update current draft content with optional commit message.
     *
     * @param namespaceId namespace ID
     * @param skillCard   skill card JSON string
     * @param setAsLatest whether set as latest (optional)
     * @param commitMsg   version-level commit message (optional)
     * @return true if update success
     * @throws NacosException if fail to update draft
     */
    @Since("3.2.1")
    boolean updateDraft(String namespaceId, String skillCard, Boolean setAsLatest, String commitMsg)
        throws NacosException;
    
    /**
     * Delete current draft version.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @return true if delete success
     * @throws NacosException if fail to delete draft
     */
    @Since("3.2.0")
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
    @Since("3.2.0")
    String submit(String namespaceId, String skillName, String version) throws NacosException;
    
    /**
     * Publish an approved reviewing version.
     *
     * @param namespaceId       namespace ID
     * @param skillName         skill name
     * @param version           version
     * @param updateLatestLabel retained for compatibility and ignored by server
     * @return true if publish success
     * @throws NacosException if fail to publish
     */
    @Since("3.2.0")
    boolean publish(String namespaceId, String skillName, String version, Boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Force-publish a skill version, bypassing pipeline validation.
     *
     * @param namespaceId       namespace ID
     * @param skillName         skill name
     * @param version           version
     * @param updateLatestLabel retained for compatibility and ignored by server
     * @return true if force-publish success
     * @throws NacosException if fail to force-publish
     */
    @Since("3.2.1")
    boolean forcePublish(String namespaceId, String skillName, String version,
        Boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Re-edit a reviewed skill version, transitioning it back to draft status.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param version     version to re-edit
     * @return true if redraft success
     * @throws NacosException if fail to redraft
     */
    @Since("3.2.2")
    boolean redraft(String namespaceId, String skillName, String version) throws NacosException;
    
    /**
     * Update runtime labels mapping JSON.
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name
     * @param labels      JSON string
     * @return true if update success
     * @throws NacosException if fail to update labels
     */
    @Since("3.2.0")
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
    @Since("3.2.0")
    boolean updateBizTags(String namespaceId, String skillName, String bizTags)
        throws NacosException;
    
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
    @Since("3.2.0")
    boolean changeOnlineStatus(String namespaceId, String skillName, String scope, String version,
        boolean online)
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
    @Since("3.2.0")
    boolean updateScope(String namespaceId, String skillName, String scope) throws NacosException;
}
