/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.prompt;

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
 * Prompt lifecycle operation service.
 *
 * <p>Manages the complete lifecycle of prompts through a double-layer storage architecture:
 * DB (ai_resource + ai_resource_version) for metadata, NacosConfig (via AiResourceStorageRouter) for content.
 * Mirrors the Skill governance model: draft → submit → reviewing (Pipeline) → publish → online/offline.</p>
 *
 * @author nacos
 */
public interface PromptOperationService {
    
    // ========== Admin APIs ==========
    
    /**
     * Create a new draft version for a prompt.
     *
     * <p>When the prompt does not exist, a new ai_resource row is created. When {@code basedOnVersion} is specified,
     * content is forked from that version. Otherwise, initial content must be provided via {@code template}.</p>
     *
     * @param namespaceId    namespace ID
     * @param promptKey      prompt key (name)
     * @param basedOnVersion base version to fork from (optional)
     * @param targetVersion  target draft version to create (optional; auto-generated when empty)
     * @param template       prompt template content (required when not forking)
     * @param variables      prompt variable definitions (optional)
     * @param commitMsg      commit message (optional)
     * @param description    prompt description (only allowed on first creation)
     * @param bizTags        biz tags JSON (only allowed on first creation)
     * @return the created draft version string
     * @throws NacosException if validation fails or conflict exists
     */
    String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, List<PromptVariable> variables, String commitMsg, String description,
        String bizTags)
        throws NacosException;
    
    /**
     * Update existing draft content.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param template    updated template content
     * @param variables   updated variable definitions
     * @param commitMsg   updated commit message
     * @throws NacosException if no editing draft exists or version is not in draft status
     */
    void updateDraft(String namespaceId, String promptKey, String template,
        List<PromptVariable> variables,
        String commitMsg) throws NacosException;
    
    /**
     * Delete current draft and release editing pointer.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @throws NacosException if prompt not found
     */
    void deleteDraft(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Submit a draft version for publish. If no pipeline plugins are configured, publishes directly.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version to submit (optional; defaults to current editing version)
     * @return the submitted version string
     * @throws NacosException if no draft version exists or submission fails
     */
    String submit(String namespaceId, String promptKey, String version) throws NacosException;
    
    /**
     * Publish a reviewing version. Pipeline must have passed when pipeline is configured.
     *
     * @param namespaceId       namespace ID
     * @param promptKey         prompt key
     * @param version           version to publish
     * @param updateLatestLabel retained for compatibility and ignored; latest is server-managed
     * @throws NacosException if version is not in reviewing/online status or pipeline not approved
     */
    void publish(String namespaceId, String promptKey, String version, boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Force-publish a prompt version, bypassing pipeline validation. Should only be invoked by admin users.
     *
     * @param namespaceId       namespace ID
     * @param promptKey         prompt key
     * @param version           version to force-publish
     * @param updateLatestLabel retained for compatibility and ignored; latest is server-managed
     * @throws NacosException if version not found
     */
    void forcePublish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Re-edit a reviewed version, transitioning it back to draft.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version to re-edit
     * @throws NacosException if version not found or not in reviewed status
     */
    void redraft(String namespaceId, String promptKey, String version) throws NacosException;
    
    /**
     * Online/offline operation for a prompt version.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     version to operate
     * @param online      true means online, false means offline
     * @throws NacosException if version not found
     */
    void changeOnlineStatus(String namespaceId, String promptKey, String version, boolean online)
        throws NacosException;
    
    /**
     * Update labels mapping (label -> version) without changing any version status.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param labels      label-to-version mapping
     * @throws NacosException if prompt not found
     */
    void updateLabels(String namespaceId, String promptKey, Map<String, String> labels)
        throws NacosException;
    
    /**
     * Update prompt biz tags.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param bizTags     biz tags JSON string
     * @throws NacosException if prompt not found
     */
    void updateBizTags(String namespaceId, String promptKey, String bizTags) throws NacosException;
    
    /**
     * Update prompt description.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param description new description
     * @throws NacosException if prompt not found
     */
    void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException;
    
    /**
     * Delete an entire prompt and all its versions (DB + storage).
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @throws NacosException if deletion fails
     */
    void deletePrompt(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Get prompt governance detail (admin view). Returns version governance metadata and all version summaries.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @return prompt meta info with governance data and version list
     * @throws NacosException if prompt not found
     */
    PromptMetaInfo getPromptDetail(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Get prompt version detail. Returns full content for a specific version.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     target version
     * @return prompt version content
     * @throws NacosException if prompt or version not found
     */
    PromptVersionInfo getPromptVersionDetail(String namespaceId, String promptKey, String version)
        throws NacosException;
    
    /**
     * Download a specific prompt version (same as getPromptVersionDetail but also publishes a download event for
     * metrics).
     *
     * @param namespaceId namespace
     * @param promptKey   prompt key
     * @param version     version string
     * @return prompt version content
     * @throws NacosException if prompt or version not found
     */
    PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey, String version)
        throws NacosException;
    
    /**
     * List prompts with pagination for admin usage.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key pattern (for search)
     * @param search      search type (accurate/blur)
     * @param bizTags     biz tags filter
     * @param pageNo      page number
     * @param pageSize    page size
     * @return paged prompt summary list
     * @throws NacosException if query fails
     */
    Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search,
        String bizTags, int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * List prompt versions with pagination.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param pageNo      page number
     * @param pageSize    page size
     * @return paged version summary list
     * @throws NacosException if prompt not found
     */
    Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey, int pageNo,
        int pageSize)
        throws NacosException;
    
    // ========== Client APIs ==========
    
    /**
     * Query prompt for runtime client usage. Priority: version > label > latest(label).
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param version     explicit version (optional)
     * @param label       route label, e.g. latest/stable (optional)
     * @return prompt version content
     * @throws NacosException if prompt not found or no matching version
     */
    PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version,
        String label)
        throws NacosException;
    
    /**
     * Refresh the latest mirror config in the legacy nacos-ai-prompt group for backward compatibility.
     * Called after publish or label update to keep old clients working.
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @throws NacosException if refresh fails
     */
    void refreshLatestMirror(String namespaceId, String promptKey) throws NacosException;
    
    // ========== Legacy compatibility APIs (deprecated) ==========
    
    /**
     * Legacy one-shot publish: creates a draft, submits it, and publishes if no pipeline is configured.
     * If a pipeline exists, only draft + submit are executed; the caller must complete the remaining
     * lifecycle steps (publish/online) via the new APIs.
     *
     * @deprecated Use {@link #createDraft} + {@link #submit} instead.
     */
    @Deprecated
    boolean publishPromptVersion(String namespaceId, String promptKey, String version,
        String template,
        String commitMsg, String description, String bizTags, List<PromptVariable> variables)
        throws NacosException;
    
    /**
     * Legacy get prompt metadata.
     *
     * @deprecated Use {@link #getPromptDetail} instead.
     */
    @Deprecated
    PromptMetaInfo getPromptMeta(String namespaceId, String promptKey) throws NacosException;
    
    /**
     * Legacy query prompt detail by version/label/latest.
     *
     * @deprecated Use {@link #getPromptVersionDetail} for admin or {@link #queryPrompt} for client.
     */
    @Deprecated
    PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version,
        String label)
        throws NacosException;
    
    /**
     * Legacy bind label to a prompt version.
     *
     * @deprecated Use {@link #updateLabels} instead.
     */
    @Deprecated
    boolean bindLabel(String namespaceId, String promptKey, String label, String version)
        throws NacosException;
    
    /**
     * Legacy unbind label from a prompt.
     *
     * @deprecated Use {@link #updateLabels} instead.
     */
    @Deprecated
    boolean unbindLabel(String namespaceId, String promptKey, String label) throws NacosException;
    
    /**
     * Legacy update prompt metadata (description and bizTags).
     *
     * @deprecated Use {@link #updateDescription} and {@link #updateBizTags} instead.
     */
    @Deprecated
    boolean updatePromptMetadata(String namespaceId, String promptKey, String description,
        String bizTags)
        throws NacosException;
}
