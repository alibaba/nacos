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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Nacos AI module AgentSpec relative maintainer service.
 *
 * @author nacos
 */
public interface AgentSpecMaintainerService {
    
    /**
     * Get agentspec detail.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name
     * @return agentspec detail
     * @throws NacosException if fail to get agentspec
     */
    @Since("3.2.0")
    AgentSpec getAgentSpecDetail(String namespaceId, String agentSpecName) throws NacosException;
    
    /**
     * Get agentspec detail with default namespace.
     *
     * @param agentSpecName agentspec name
     * @return agentspec detail
     * @throws NacosException if fail to get agentspec
     */
    @Since("3.2.0")
    default AgentSpec getAgentSpecDetail(String agentSpecName) throws NacosException {
        return getAgentSpecDetail(Constants.DEFAULT_NAMESPACE_ID, agentSpecName);
    }
    
    /**
     * Get agentspec admin detail.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @return admin detail
     * @throws NacosException if fail to get agentspec admin detail
     */
    @Since("3.2.0")
    AgentSpecMeta getAgentSpecAdminDetail(String namespaceId, String agentSpecName)
        throws NacosException;
    
    /**
     * Get specific agentspec version detail.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param version agentspec version
     * @return agentspec version detail
     * @throws NacosException if fail to get agentspec version detail
     */
    @Since("3.2.0")
    AgentSpec getAgentSpecVersionDetail(String namespaceId, String agentSpecName, String version)
        throws NacosException;
    
    /**
     * Get specific agentspec version detail with default namespace.
     *
     * @param agentSpecName agentspec name
     * @param version agentspec version
     * @return agentspec version detail
     * @throws NacosException if fail to get agentspec version detail
     */
    @Since("3.2.0")
    default AgentSpec getAgentSpecVersionDetail(String agentSpecName, String version)
        throws NacosException {
        return getAgentSpecVersionDetail(Constants.DEFAULT_NAMESPACE_ID, agentSpecName, version);
    }
    
    /**
     * Get specific agentspec version metadata without resource content. Returns the agentspec main content and resource
     * list (name + type only), skipping resource file IO.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param version agentspec version
     * @return agentspec with resource list containing only name and type
     * @throws NacosException if fail to get agentspec version meta
     */
    @Since("3.2.1")
    AgentSpec getAgentSpecVersionMeta(String namespaceId, String agentSpecName, String version)
        throws NacosException;
    
    /**
     * Get specific agentspec version metadata with default namespace.
     *
     * @param agentSpecName agentspec name
     * @param version agentspec version
     * @return agentspec with resource list containing only name and type
     * @throws NacosException if fail to get agentspec version meta
     */
    @Since("3.2.1")
    default AgentSpec getAgentSpecVersionMeta(String agentSpecName, String version)
        throws NacosException {
        return getAgentSpecVersionMeta(Constants.DEFAULT_NAMESPACE_ID, agentSpecName, version);
    }
    
    /**
     * Delete agentspec.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name
     * @return true if delete success
     * @throws NacosException if fail to delete agentspec
     */
    @Since("3.2.0")
    boolean deleteAgentSpec(String namespaceId, String agentSpecName) throws NacosException;
    
    /**
     * Delete agentspec with default namespace.
     *
     * @param agentSpecName agentspec name
     * @return true if delete success
     * @throws NacosException if fail to delete agentspec
     */
    @Since("3.2.0")
    default boolean deleteAgentSpec(String agentSpecName) throws NacosException {
        return deleteAgentSpec(Constants.DEFAULT_NAMESPACE_ID, agentSpecName);
    }
    
    /**
     * List agentspecs with pagination.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name pattern for filtering
     * @param search         search mode: "accurate" or "blur"
     * @param pageNo         page number
     * @param pageSize       page size
     * @return paged agentspec list
     * @throws NacosException if fail to list agentspecs
     */
    @Since("3.2.0")
    Page<AgentSpecBasicInfo> listAgentSpecs(String namespaceId, String agentSpecName, String search,
        int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * List agentspecs with default namespace.
     *
     * @param agentSpecName agentspec name pattern for filtering
     * @param pageNo        page number
     * @param pageSize      page size
     * @return paged agentspec list
     * @throws NacosException if fail to list agentspecs
     */
    @Since("3.2.0")
    default Page<AgentSpecBasicInfo> listAgentSpecs(String agentSpecName, int pageNo, int pageSize)
        throws NacosException {
        return listAgentSpecs(Constants.DEFAULT_NAMESPACE_ID, agentSpecName, "blur", pageNo,
            pageSize);
    }
    
    /**
     * List agentspec admin items with governance metadata.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name pattern for filtering
     * @param search search mode
     * @param pageNo page number
     * @param pageSize page size
     * @return paged admin list
     * @throws NacosException if fail to list agentspec admin items
     */
    @Since("3.2.0")
    Page<AgentSpecSummary> listAgentSpecAdminItems(String namespaceId, String agentSpecName,
        String search,
        int pageNo, int pageSize) throws NacosException;
    
    /**
     * List agentspec admin items with pagination, optional ordering and additional filter criteria.
     *
     * <p>Backward-compatible: when {@code orderBy}, {@code owner} and {@code scope} are all {@code null}/empty,
     * the behaviour is identical to {@link #listAgentSpecAdminItems(String, String, String, int, int)}.</p>
     *
     * @param namespaceId   namespace ID
     * @param agentSpecName agentspec name pattern for filtering
     * @param search        search mode
     * @param orderBy       optional sort field (e.g. "download_count"); null defaults to gmt_modified
     * @param owner         optional filter by resource owner; null or empty means no owner filter
     * @param scope         optional filter by visibility scope ("PUBLIC"/"PRIVATE"); null or empty means no scope filter
     * @param pageNo        page number
     * @param pageSize      page size
     * @return paged admin list
     * @throws NacosException if fail to list agentspec admin items
     */
    @Since("3.2.1")
    default Page<AgentSpecSummary> listAgentSpecAdminItems(String namespaceId, String agentSpecName,
        String search,
        String orderBy, String owner, String scope, int pageNo, int pageSize)
        throws NacosException {
        return listAgentSpecAdminItems(namespaceId, agentSpecName, search, pageNo, pageSize);
    }
    
    /**
     * Upload agentspec from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes    zip file bytes
     * @return agentspec name
     * @throws NacosException if fail to upload agentspec
     */
    @Since("3.2.0")
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
     * @throws NacosException if fail to upload agentspec
     */
    @Since("3.2.0")
    String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException;
    
    /**
     * Upload agentspec from zip file with default namespace.
     *
     * @param zipBytes zip file bytes
     * @return agentspec name
     * @throws NacosException if fail to upload agentspec
     */
    @Since("3.2.0")
    default String uploadAgentSpecFromZip(byte[] zipBytes) throws NacosException {
        return uploadAgentSpecFromZip(Constants.DEFAULT_NAMESPACE_ID, zipBytes, false);
    }
    
    /**
     * Create draft version for an agentspec.
     *
     * @param namespaceId     namespace ID
     * @param agentSpecName   agentspec name
     * @param basedOnVersion  base version (optional)
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    @Since("3.2.0")
    default String createDraft(String namespaceId, String agentSpecName, String basedOnVersion)
        throws NacosException {
        return createDraft(namespaceId, agentSpecName, basedOnVersion, null);
    }
    
    /**
     * Create draft version for an agentspec.
     *
     * @param namespaceId     namespace ID
     * @param agentSpecName   agentspec name
     * @param basedOnVersion  base version (optional)
     * @param targetVersion   target version (optional, auto-increment if blank)
     * @return created draft version
     * @throws NacosException if fail to create draft
     */
    @Since("3.2.1")
    String createDraft(String namespaceId, String agentSpecName, String basedOnVersion,
        String targetVersion)
        throws NacosException;
    
    /**
     * Update current draft content.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecCard  agentspec card JSON string
     * @param setAsLatest    whether set as latest (optional)
     * @return true if update success
     * @throws NacosException if fail to update draft
     */
    @Since("3.2.0")
    boolean updateDraft(String namespaceId, String agentSpecCard, Boolean setAsLatest)
        throws NacosException;
    
    /**
     * Delete current draft version.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name
     * @return true if delete success
     * @throws NacosException if fail to delete draft
     */
    @Since("3.2.0")
    boolean deleteDraft(String namespaceId, String agentSpecName) throws NacosException;
    
    /**
     * Submit a version for pipeline review.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name
     * @param version        version (optional, server may choose current editing)
     * @return submit result (e.g. pipeline id)
     * @throws NacosException if fail to submit
     */
    @Since("3.2.0")
    String submit(String namespaceId, String agentSpecName, String version) throws NacosException;
    
    /**
     * Publish an approved reviewing version.
     *
     * @param namespaceId        namespace ID
     * @param agentSpecName      agentspec name
     * @param version            version
     * @param updateLatestLabel  retained for compatibility and ignored by server
     * @return true if publish success
     * @throws NacosException if fail to publish
     */
    @Since("3.2.0")
    boolean publish(String namespaceId, String agentSpecName, String version,
        Boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Force-publish an agentspec version, bypassing pipeline validation.
     *
     * @param namespaceId       namespace ID
     * @param agentSpecName     agentspec name
     * @param version           version
     * @param updateLatestLabel retained for compatibility and ignored by server
     * @return true if force-publish success
     * @throws NacosException if fail to force-publish
     */
    @Since("3.2.1")
    boolean forcePublish(String namespaceId, String agentSpecName, String version,
        Boolean updateLatestLabel)
        throws NacosException;
    
    /**
     * Re-edit a reviewed agent spec version, transitioning it back to draft status.
     *
     * @param namespaceId   namespace ID
     * @param agentSpecName agent spec name
     * @param version       version to re-edit
     * @return true if redraft success
     * @throws NacosException if fail to redraft
     */
    @Since("3.2.2")
    boolean redraft(String namespaceId, String agentSpecName, String version) throws NacosException;
    
    /**
     * Update runtime labels mapping JSON.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name
     * @param labels         JSON string
     * @return true if update success
     * @throws NacosException if fail to update labels
     */
    @Since("3.2.0")
    boolean updateLabels(String namespaceId, String agentSpecName, String labels)
        throws NacosException;
    
    /**
     * Update agentspec biz tags JSON.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param bizTags biz tags JSON string
     * @return true if update success
     * @throws NacosException if fail to update biz tags
     */
    @Since("3.2.0")
    boolean updateBizTags(String namespaceId, String agentSpecName, String bizTags)
        throws NacosException;
    
    /**
     * Online/offline operation.
     *
     * @param namespaceId    namespace ID
     * @param agentSpecName  agentspec name
     * @param scope          "agentspec" for agentspec-level enable/disable; otherwise version-level
     * @param version        version for version-level (optional)
     * @param online         true for online(enable), false for offline(disable)
     * @return true if operation success
     * @throws NacosException if fail to change status
     */
    @Since("3.2.0")
    boolean changeOnlineStatus(String namespaceId, String agentSpecName, String scope,
        String version,
        boolean online) throws NacosException;
    
    /**
     * Update agentspec visibility scope.
     *
     * @param namespaceId namespace ID
     * @param agentSpecName agentspec name
     * @param scope scope value, e.g. PUBLIC/PRIVATE
     * @return true if update success
     * @throws NacosException if fail to update scope
     */
    @Since("3.2.0")
    boolean updateScope(String namespaceId, String agentSpecName, String scope)
        throws NacosException;
}
