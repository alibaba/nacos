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

import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.Map;

/**
 * Skill operation service.
 *
 * @author nacos
 */
public interface SkillOperationService {
    
    // ========== Admin APIs ==========
    
    /**
     * Upload skill from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @return skill name
     * @throws NacosException if upload failed
     */
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, false);
    }
    
    /**
     * Upload skill from zip file with original upload file name.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @param zipFileName uploaded zip file name (optional, used to infer version)
     * @param overwrite whether to overwrite the current editable draft when the skill already exists
     * @return skill name
     * @throws NacosException if upload failed
     */
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes, String zipFileName,
        boolean overwrite)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, zipFileName, overwrite, null);
    }
    
    /**
     * Upload skill from zip file with original upload file name and optional target version.
     *
     * @param namespaceId   namespace ID
     * @param zipBytes      zip file bytes
     * @param zipFileName   uploaded zip file name (optional, used to infer version)
     * @param overwrite     whether to overwrite the current editable draft when the skill already exists
     * @param targetVersion user-specified version (optional, used as fallback when ZIP content has no version)
     * @return skill name
     * @throws NacosException if upload failed
     */
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes, String zipFileName,
        boolean overwrite,
        String targetVersion) throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite, targetVersion);
    }
    
    /**
     * Upload skill from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @param overwrite whether to overwrite the current editable draft when the skill already exists
     * @return skill name
     * @throws NacosException if upload failed
     */
    default String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite, null);
    }
    
    /**
     * Upload skill from zip file with optional target version.
     *
     * @param namespaceId   namespace ID
     * @param zipBytes      zip file bytes
     * @param overwrite     whether to overwrite the current editable draft when the skill already exists
     * @param targetVersion user-specified version (optional, used as fallback when ZIP content has no version)
     * @return skill name
     * @throws NacosException if upload failed
     */
    String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion)
        throws NacosException;
    
    /**
     * Batch upload multiple skills from a single zip archive. The zip must contain one-level subdirectories,
     * each with its own SKILL.md. Uses best-effort strategy: processes all skills individually, returning
     * succeeded and failed lists.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes containing multiple skill subdirectories
     * @param overwrite whether to overwrite existing drafts
     * @return batch upload result with succeeded and failed skill names
     * @throws NacosException if zip parsing fails entirely (e.g. invalid format, no SKILL.md found)
     */
    BatchUploadResult batchUploadSkillsFromZip(String namespaceId, byte[] zipBytes,
        boolean overwrite)
        throws NacosException;
    
    /**
     * Bootstrap skill from zip file as an online skill.
     *
     * <p>This is intended for server-side built-in data initialization and bypasses draft/pipeline flow.</p>
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @throws NacosException if bootstrap failed
     */
    void bootstrapSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException;
    
    /**
     * Bootstrap skill from zip file as an online skill with source metadata.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @param from source identifier, e.g. github.com/nacos
     * @throws NacosException if bootstrap failed
     */
    default void bootstrapSkillFromZip(String namespaceId, byte[] zipBytes, String from)
        throws NacosException {
        bootstrapSkillFromZip(namespaceId, zipBytes);
    }
    
    /**
     * Get skill detail for admin usage. Returns version governance metadata and all version summaries.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @return skill admin detail (governance info + version summaries)
     * @throws NacosException if skill not found
     */
    SkillMeta getSkillDetail(String namespaceId, String skillName) throws NacosException;
    
    /**
     * Get skill version detail for admin usage. Returns full skill content for a specific version, used for viewing or editing.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @param version target version
     * @return full skill content for the specified version
     * @throws NacosException if skill or version not found
     */
    Skill getSkillVersionDetail(String namespaceId, String skillName, String version)
        throws NacosException;
    
    /**
     * Download skill version. Semantically identical to {@link #getSkillVersionDetail} but provides a separate
     * entry point so that download events can be tracked independently (e.g. download count statistics).
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @param version target version
     * @return full skill content for the specified version
     * @throws NacosException if skill or version not found
     */
    Skill downloadSkillVersion(String namespaceId, String skillName, String version)
        throws NacosException;
    
    /**
     * Delete skill.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @throws NacosException if delete failed
     */
    void deleteSkill(String namespaceId, String skillName) throws NacosException;
    
    /**
     * List skills with pagination for admin usage. Returns full governance metadata.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name (for search)
     * @param search search type (accurate/blur)
     * @param pageNo page number
     * @param pageSize page size
     * @return skill admin list page with governance metadata
     * @throws NacosException if query failed
     */
    Page<SkillSummary> listSkills(String namespaceId, String skillName, String search, int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * List skills with pagination and optional ordering for admin usage.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name (for search)
     * @param search search type (accurate/blur)
     * @param orderBy sort field (e.g. "download_count"), null defaults to gmt_modified
     * @param pageNo page number
     * @param pageSize page size
     * @return skill admin list page with governance metadata
     * @throws NacosException if query failed
     */
    Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        int pageNo, int pageSize) throws NacosException;
    
    /**
     * List skills with pagination, optional ordering, and additional filter criteria for admin usage.
     *
     * <p>Backward-compatible: when {@code owner} and {@code scope} are both {@code null}/empty,
     * the behaviour is identical to
     * {@link #listSkills(String, String, String, String, int, int)}.</p>
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name (for search)
     * @param search      search type (accurate/blur)
     * @param orderBy     sort field (e.g. "download_count"), null defaults to gmt_modified
     * @param owner       optional filter by resource owner; null or empty means no owner filter
     * @param scope       optional filter by visibility scope ("PUBLIC"/"PRIVATE"); null or empty means no scope filter
     * @param pageNo      page number
     * @param pageSize    page size
     * @return skill admin list page with governance metadata
     * @throws NacosException if query failed
     */
    default Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        String owner, String scope, int pageNo, int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, orderBy, owner, scope, null, pageNo,
            pageSize);
    }
    
    /**
     * List skills with pagination, optional ordering, and additional filter criteria including bizTag for admin usage.
     *
     * <p>Backward-compatible: when {@code owner}, {@code scope} and {@code bizTag} are all {@code null}/empty,
     * the behaviour is identical to
     * {@link #listSkills(String, String, String, String, int, int)}.</p>
     *
     * @param namespaceId namespace ID
     * @param skillName   skill name (for search)
     * @param search      search type (accurate/blur)
     * @param orderBy     sort field (e.g. "download_count"), null defaults to gmt_modified
     * @param owner       optional filter by resource owner; null or empty means no owner filter
     * @param scope       optional filter by visibility scope ("PUBLIC"/"PRIVATE"); null or empty means no scope filter
     * @param bizTag      optional filter by business tag (fuzzy match on bizTags column); null or empty means no filter
     * @param pageNo      page number
     * @param pageSize    page size
     * @return skill admin list page with governance metadata
     * @throws NacosException if query failed
     */
    Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        String owner, String scope, String bizTag, int pageNo, int pageSize) throws NacosException;
    
    /**
     * Create a new draft version.
     * <p>
     * {@code initialContent} is required for a brand-new skill or when no published version exists to fork from.
     * When forking from an existing version, {@code initialContent} must be null and content is copied from the base
     * version.
     * </p>
     *
     * @param namespaceId namespace ID
     * @param name skill name
     * @param basedOnVersion base version to fork from (optional; defaults per server rules when resolving base)
     * @param targetVersion target draft version to create (optional; auto-generated when empty)
     * @param initialContent full skill from {@code skillCard}, or null when forking
     * @param commitMsg version-level commit message describing what changed (optional; stored empty when not provided,
     *                  not derived from skill description)
     * @return created draft version
     */
    String createDraft(String namespaceId, String name, String basedOnVersion, String targetVersion,
        Skill initialContent, String commitMsg)
        throws NacosException;
    
    /**
     * Update existing draft content.
     *
     * @param namespaceId namespace ID
     * @param draftSkill full skill content to write into draft
     * @param commitMsg version-level commit message describing what changed (optional; updates version desc when not blank)
     */
    void updateDraft(String namespaceId, Skill draftSkill, String commitMsg) throws NacosException;
    
    /**
     * Delete current draft and release working pointer.
     */
    void deleteDraft(String namespaceId, String name) throws NacosException;
    
    /**
     * Submit a draft version for publish. If no pipeline plugins configured, will directly publish.
     *
     * @return submit result identifier or current version
     */
    String submit(String namespaceId, String name, String version) throws NacosException;
    
    /**
     * Publish a reviewing version. Must have pipeline all passed when pipeline exists.
     */
    void publish(String namespaceId, String name, String version, boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Force-publish a skill version, bypassing pipeline validation.
     * Accepts draft (pipeline-rejected) and reviewing (pipeline in-progress) versions.
     * Should only be invoked by admin users.
     *
     * @param namespaceId      namespace ID
     * @param name             skill name
     * @param version          version to force-publish
     * @param updateLatestLabel whether to update the "latest" label
     */
    void forcePublish(String namespaceId, String name, String version, boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Re-edit a reviewed version, transitioning it back to draft.
     *
     * @param namespaceId namespace
     * @param name        skill name
     * @param version     version to re-edit
     */
    void redraft(String namespaceId, String name, String version) throws NacosException;
    
    /**
     * Update labels mapping (label -> version) without changing any version status.
     */
    void updateLabels(String namespaceId, String name, Map<String, String> labels)
        throws NacosException;
    
    /**
     * Update skill biz tags JSON.
     */
    void updateBizTags(String namespaceId, String name, String bizTags) throws NacosException;
    
    /**
     * Online/offline operation.
     *
     * @param scope "skill" for global enable/disable, otherwise version scope
     * @param version version to operate when scope is version-level
     * @param online true means online/enable, false means offline/disable
     */
    void changeOnlineStatus(String namespaceId, String name, String scope, String version,
        boolean online) throws NacosException;
    
    /**
     * Update skill visibility scope (PUBLIC or PRIVATE). Only the owner or users with explicit write permission can
     * change the scope.
     *
     * @param namespaceId namespace ID
     * @param name        skill name
     * @param scope       target scope: PUBLIC or PRIVATE
     * @throws NacosException if skill not found or no permission
     */
    void updateScope(String namespaceId, String name, String scope) throws NacosException;
    
    // ========== Client APIs ==========
    
    /**
     * Search skills for runtime client usage. Only returns enabled skills that have at least one online version.
     * Returns only name and description for client consumption.
     *
     * @param namespaceId namespace ID
     * @param keyword keyword (optional)
     * @param pageNo page number
     * @param pageSize page size
     */
    Page<SkillBasicInfo> searchSkills(String namespaceId, String keyword, int pageNo, int pageSize)
        throws NacosException;
    
    /**
     * Query skill for runtime client usage. Priority: label > version > latest(label).
     *
     * @param namespaceId namespace ID
     * @param name skill name
     * @param version explicit version (optional)
     * @param label route label, e.g. latest/stable (optional)
     */
    Skill querySkill(String namespaceId, String name, String version, String label)
        throws NacosException;
}
