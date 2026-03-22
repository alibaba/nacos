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

import com.alibaba.nacos.ai.model.skills.SkillAdminDetail;
import com.alibaba.nacos.ai.model.skills.SkillAdminListItem;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
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
    String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException;

    /**
     * Get skill detail for admin usage. Returns version governance metadata and all version summaries.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @return skill admin detail (governance info + version summaries)
     * @throws NacosException if skill not found
     */
    SkillAdminDetail getSkillDetail(String namespaceId, String skillName) throws NacosException;

    /**
     * Get skill version detail for admin usage. Returns full skill content for a specific version, used for viewing or editing.
     *
     * @param namespaceId namespace ID
     * @param skillName skill name
     * @param version target version
     * @return full skill content for the specified version
     * @throws NacosException if skill or version not found
     */
    Skill getSkillVersionDetail(String namespaceId, String skillName, String version) throws NacosException;

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
    Skill downloadSkillVersion(String namespaceId, String skillName, String version) throws NacosException;

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
    Page<SkillAdminListItem> listSkills(String namespaceId, String skillName, String search, int pageNo, int pageSize) throws NacosException;

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
    Page<SkillAdminListItem> listSkills(String namespaceId, String skillName, String search, String orderBy,
            int pageNo, int pageSize) throws NacosException;

    /**
     * Create a new draft version based on latest or specified version.
     * If no base version is specified and no online version exists, an empty skill draft will be created.
     *
     * @param namespaceId namespace ID
     * @param name skill name
     * @param basedOnVersion base version (optional, default latest)
     * @return created draft version
     */
    String createDraft(String namespaceId, String name, String basedOnVersion) throws NacosException;

    /**
     * Update existing draft content.
     *
     * @param namespaceId namespace ID
     * @param draftSkill full skill content to write into draft
     */
    void updateDraft(String namespaceId, Skill draftSkill) throws NacosException;

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
    void publish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException;

    /**
     * Update labels mapping (label -> version) without changing any version status.
     */
    void updateLabels(String namespaceId, String name, Map<String, String> labels) throws NacosException;

    /**
     * Online/offline operation.
     *
     * @param scope "skill" for global enable/disable, otherwise version scope
     * @param version version to operate when scope is version-level
     * @param online true means online/enable, false means offline/disable
     */
    void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online) throws NacosException;

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
    Page<SkillBasicInfo> searchSkills(String namespaceId, String keyword, int pageNo, int pageSize) throws NacosException;

    /**
     * Query skill for runtime client usage. Priority: label > version > latest(label).
     *
     * @param namespaceId namespace ID
     * @param name skill name
     * @param version explicit version (optional)
     * @param label route label, e.g. latest/stable (optional)
     */
    Skill querySkill(String namespaceId, String name, String version, String label) throws NacosException;
}
