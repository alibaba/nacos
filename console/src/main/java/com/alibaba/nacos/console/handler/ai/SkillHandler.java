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

import com.alibaba.nacos.ai.form.skills.admin.SkillDraftCreateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillLabelsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillOnlineForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillSubmitForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.ai.model.skills.SkillAdminDetail;
import com.alibaba.nacos.ai.model.skills.SkillAdminListItem;
import com.alibaba.nacos.api.ai.model.skills.Skill;
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
    SkillAdminDetail getSkill(SkillForm form) throws NacosException;

    /**
     * Get skill version detail. Returns full skill content for a specific version.
     *
     * @param form skill form (with version)
     * @return full skill content
     * @throws NacosException nacos exception
     */
    Skill getSkillVersion(SkillForm form) throws NacosException;

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
    Page<SkillAdminListItem> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException;
    
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
     * Create draft version based on latest or a specified version.
     *
     * @param form draft create form
     * @return created draft version
     * @throws NacosException if operation failed
     */
    String createDraft(SkillDraftCreateForm form) throws NacosException;

    /**
     * Update current draft content.
     *
     * @param form update form
     * @throws NacosException if operation failed
     */
    void updateDraft(SkillUpdateForm form) throws NacosException;

    /**
     * Delete current draft version.
     *
     * @param form skill form
     * @throws NacosException if operation failed
     */
    void deleteDraft(SkillForm form) throws NacosException;

    /**
     * Submit a version for pipeline review.
     *
     * @param form submit form
     * @return submit result (e.g. pipeline id)
     * @throws NacosException if operation failed
     */
    String submit(SkillSubmitForm form) throws NacosException;

    /**
     * Publish an approved reviewing version.
     *
     * @param form publish form
     * @throws NacosException if operation failed
     */
    void publish(SkillPublishForm form) throws NacosException;

    /**
     * Update runtime route labels without changing version status.
     *
     * @param form labels update form
     * @throws NacosException if operation failed
     */
    void updateLabels(SkillLabelsUpdateForm form) throws NacosException;

    /**
     * Change online/offline status.
     *
     * @param form online form
     * @param online true for online, false for offline
     * @throws NacosException if operation failed
     */
    void changeOnlineStatus(SkillOnlineForm form, boolean online) throws NacosException;
}
