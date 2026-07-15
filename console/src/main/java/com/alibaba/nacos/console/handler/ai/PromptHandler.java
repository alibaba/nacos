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

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;
import java.util.Map;

/**
 * Prompt handler interface.
 *
 * @author nacos
 */
public interface PromptHandler {
    
    // ========== Common APIs ==========
    
    /**
     * Delete prompt.
     */
    boolean deletePrompt(PromptForm form, String srcUser, String srcIp) throws NacosException;
    
    /**
     * List prompts with pagination.
     */
    Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException;
    
    /**
     * List prompt versions page.
     */
    Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form) throws NacosException;
    
    // ========== Lifecycle APIs ==========
    
    /**
     * Get prompt governance detail.
     */
    PromptMetaInfo getPromptGovernanceDetail(String namespaceId, String promptKey)
        throws NacosException;
    
    /**
     * Get specific version detail.
     */
    PromptVersionInfo getVersionDetail(String namespaceId, String promptKey, String version)
        throws NacosException;
    
    /**
     * Download a specific prompt version (triggers download count increment).
     */
    PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey, String version)
        throws NacosException;
    
    /**
     * Create draft version.
     */
    String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, List<PromptVariable> variables, String commitMsg, String description,
        String bizTags)
        throws NacosException;
    
    /**
     * Update draft content.
     */
    void updateDraft(String namespaceId, String promptKey, String template,
        List<PromptVariable> variables,
        String commitMsg) throws NacosException;
    
    /**
     * Delete draft.
     */
    void deleteDraft(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Submit for review.
     */
    String submit(String namespaceId, String promptKey, String version) throws NacosException;
    
    /**
     * Publish a reviewed version.
     */
    void publish(String namespaceId, String promptKey, String version, boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Force-publish bypassing pipeline.
     */
    void forcePublish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Re-edit a reviewed version, transitioning it back to draft status.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version to re-edit
     * @throws NacosException if operation failed
     */
    void redraft(String namespaceId, String promptKey, String version) throws NacosException;
    
    /**
     * Online or offline a version.
     */
    void changeOnlineStatus(String namespaceId, String promptKey, String version, boolean online)
        throws NacosException;
    
    /**
     * Update labels.
     */
    void updateLabels(String namespaceId, String promptKey, Map<String, String> labels)
        throws NacosException;
    
    /**
     * Update prompt description.
     */
    void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException;
    
    /**
     * Update biz tags.
     */
    void updateBizTags(String namespaceId, String promptKey, String bizTags) throws NacosException;
}
