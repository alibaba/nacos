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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.Map;

/**
 * AgentSpec operation service.
 *
 * @author nacos
 */
public interface AgentSpecOperationService {
    
    /**
     * Get agentspec detail for admin usage. Returns full agentspec content plus version governance info.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param version explicit version to query, optional
     * @return agentspec admin detail (agentspec content + governance info)
     * @throws NacosException if agentspec not found
     */
    AgentSpecMeta getAgentSpecDetail(String namespaceId, String agentSpecName, String version)
        throws NacosException;
    
    /**
     * Get agentspec detail for admin usage. Returns version governance metadata and all version summaries, without
     * specific version content. Mirrors {@code SkillOperationService#getSkillDetail(String, String)}.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @return agentspec admin detail (governance info + version summaries)
     * @throws NacosException if agentspec not found
     */
    AgentSpecMeta getAgentSpecDetail(String namespaceId, String agentSpecName)
        throws NacosException;
    
    /**
     * Get agentspec version detail for admin usage. Returns full agentspec content for a specific version, used for
     * viewing or editing. Mirrors {@code SkillOperationService#getSkillVersionDetail(String, String, String)}.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param version target version
     * @return full agentspec content for the specified version
     * @throws NacosException if agentspec or version not found
     */
    AgentSpec getAgentSpecVersionDetail(String namespaceId, String agentSpecName, String version)
        throws NacosException;
    
    /**
     * Get agentspec version metadata without resource content. Returns the agentspec main content and resource list
     * (name + type only), skipping resource file IO entirely.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param version target version
     * @return agentspec with resource list containing only name and type (no content or metadata)
     * @throws NacosException if agentspec or version not found
     */
    AgentSpec getAgentSpecVersionMeta(String namespaceId, String agentSpecName, String version)
        throws NacosException;
    
    /**
     * Delete agentspec.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @throws NacosException if delete failed
     */
    void deleteAgentSpec(String namespaceId, String agentSpecName) throws NacosException;
    
    /**
     * List agentspecs with pagination for admin usage. Returns full governance metadata.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name (for search)
     * @param search search type (accurate/blur)
     * @param pageNo page number
     * @param pageSize page size
     * @return agentspec admin list page with governance metadata
     * @throws NacosException if query failed
     */
    Page<AgentSpecSummary> listAgentSpecs(String namespaceId, String agentSpecName, String search,
        int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * List agentspecs with pagination, optional ordering, and additional filter criteria for admin usage.
     *
     * <p>Backward-compatible: when {@code orderBy}, {@code owner} and {@code scope} are all {@code null}/empty,
     * the behaviour is identical to
     * {@link #listAgentSpecs(String, String, String, int, int)}.</p>
     *
     * @param namespaceId   namespace ID
     * @param agentSpecName agentspec name (for search)
     * @param search        search type (accurate/blur)
     * @param orderBy       sort field (e.g. "download_count"), null defaults to gmt_modified
     * @param owner         optional filter by resource owner; null or empty means no owner filter
     * @param scope         optional filter by visibility scope ("PUBLIC"/"PRIVATE"); null or empty means no scope filter
     * @param pageNo        page number
     * @param pageSize      page size
     * @return agentspec admin list page with governance metadata
     * @throws NacosException if query failed
     */
    default Page<AgentSpecSummary> listAgentSpecs(String namespaceId, String agentSpecName,
        String search,
        String orderBy, String owner, String scope, int pageNo, int pageSize)
        throws NacosException {
        return listAgentSpecs(namespaceId, agentSpecName, search, pageNo, pageSize);
    }
    
    /**
     * Upload agentspec from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @return agentspec name
     * @throws NacosException if upload failed
     */
    default String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes)
        throws NacosException {
        return uploadAgentSpecFromZip(namespaceId, zipBytes, false);
    }
    
    /**
     * Upload agentspec from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @param overwrite whether to overwrite the current editable draft when the agentspec already exists
     * @return agentspec name
     * @throws NacosException if upload failed
     */
    String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException;
    
    /**
     * Bootstrap agentspec from zip file as an online agentspec.
     *
     * <p>This is intended for server-side built-in data initialization and bypasses draft/pipeline flow.</p>
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @throws NacosException if bootstrap failed
     */
    void bootstrapAgentSpecFromZip(String namespaceId, byte[] zipBytes) throws NacosException;
    
    /**
     * Bootstrap agentspec from zip file as an online agentspec with source metadata.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @param from source identifier, e.g. github.com/nacos
     * @throws NacosException if bootstrap failed
     */
    default void bootstrapAgentSpecFromZip(String namespaceId, byte[] zipBytes, String from)
        throws NacosException {
        bootstrapAgentSpecFromZip(namespaceId, zipBytes);
    }
    
    /**
     * Search agentspecs for runtime client usage. Only returns enabled agentspecs that have at least one online
     * version. Returns only name and description for client consumption.
     *
     * @param namespaceId namespace ID
     * @param keyword keyword (optional)
     * @param pageNo page number
     * @param pageSize page size
     * @return paginated list of agentspec basic info
     * @throws NacosException if search failed
     */
    Page<AgentSpecBasicInfo> searchAgentSpecs(String namespaceId, String keyword, int pageNo,
        int pageSize)
        throws NacosException;
    
    /**
     * Query agentspec for runtime client usage. Priority: label > version > latest(label).
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param version explicit version (optional)
     * @param label route label, e.g. latest/stable (optional)
     * @return resolved agentspec
     * @throws NacosException if agentspec not found
     */
    AgentSpec queryAgentSpec(String namespaceId, String name, String version, String label)
        throws NacosException;
    
    /**
     * Query agentspec for client listener path with MD5-based not-modified semantics.
     *
     * <p>When {@code clientMd5} is non-blank and equals the published content MD5 of the
     * resolved version, the returned result has {@link AgentSpecQueryResult#isNotModified()}
     * set to {@code true} and {@link AgentSpecQueryResult#getAgentSpec()} left {@code null},
     * so the controller can return HTTP 304 without loading content.
     *
     * @param namespaceId namespace ID
     * @param name        agentspec name
     * @param version     explicit version (optional)
     * @param label       route label (optional)
     * @param clientMd5   MD5 carried by the listener; may be null or blank for first poll
     * @return resolved agentspec plus its content MD5 and resolved version, or a not-modified marker
     * @throws NacosException if resolution or load fails
     */
    AgentSpecQueryResult queryAgentSpecForClient(String namespaceId, String name,
        String version, String label, String clientMd5) throws NacosException;
    
    /**
     * Create a new draft version based on latest or specified version.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param basedOnVersion base version (optional, default latest)
     * @param targetVersion target version (optional, auto-increment if blank)
     * @return created draft version
     * @throws NacosException if draft creation failed
     */
    String createDraft(String namespaceId, String name, String basedOnVersion, String targetVersion)
        throws NacosException;
    
    /**
     * Update existing draft content.
     *
     * @param namespaceId namespace ID
     * @param draftAgentSpec full agentspec content to write into draft
     * @throws NacosException if update failed
     */
    void updateDraft(String namespaceId, AgentSpec draftAgentSpec) throws NacosException;
    
    /**
     * Delete current draft and release working pointer.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @throws NacosException if delete failed
     */
    void deleteDraft(String namespaceId, String name) throws NacosException;
    
    /**
     * Submit a draft version for publish. If no pipeline plugins configured, will directly publish.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param version version to submit
     * @return submit result identifier or current version
     * @throws NacosException if submit failed
     */
    String submit(String namespaceId, String name, String version) throws NacosException;
    
    /**
     * Publish a reviewing version. Must have pipeline all passed when pipeline exists.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param version version to publish
     * @param updateLatestLabel retained for compatibility and ignored; latest is server-managed
     * @throws NacosException if publish failed
     */
    void publish(String namespaceId, String name, String version, boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Force-publish an agentspec version, bypassing pipeline validation.
     * Accepts draft, reviewing, and reviewed versions.
     * Should only be invoked by admin users.
     *
     * @param namespaceId       namespace ID
     * @param name              agentspec name
     * @param version           version to force-publish
     * @param updateLatestLabel retained for compatibility and ignored; latest is server-managed
     */
    void forcePublish(String namespaceId, String name, String version, boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Re-edit a reviewed version, transitioning it back to draft.
     *
     * @param namespaceId namespace ID
     * @param name        agentspec name
     * @param version     version to re-edit
     * @throws NacosException if version not found or not in reviewed status
     */
    void redraft(String namespaceId, String name, String version) throws NacosException;
    
    /**
     * Update labels mapping (label -> version) without changing any version status.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param labels label-to-version mapping
     * @throws NacosException if update failed
     */
    void updateLabels(String namespaceId, String name, Map<String, String> labels)
        throws NacosException;
    
    /**
     * Update agentspec biz tags JSON.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param bizTags biz tags JSON string
     * @throws NacosException if update failed
     */
    void updateBizTags(String namespaceId, String name, String bizTags) throws NacosException;
    
    /**
     * Online/offline operation.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param scope "agentspec" for global enable/disable, otherwise version scope
     * @param version version to operate when scope is version-level
     * @param online true means online/enable, false means offline/disable
     * @throws NacosException if operation failed
     */
    void changeOnlineStatus(String namespaceId, String name, String scope, String version,
        boolean online)
        throws NacosException;
    
    /**
     * Update agentspec visibility scope (PUBLIC or PRIVATE). Only the owner or users with explicit write permission
     * can change the scope.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param scope target scope: PUBLIC or PRIVATE
     * @throws NacosException if agentspec not found or no permission
     */
    void updateScope(String namespaceId, String name, String scope) throws NacosException;
}
