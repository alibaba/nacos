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

import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminDetail;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminListItem;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
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
    AgentSpecAdminDetail getAgentSpecDetail(String namespaceId, String agentSpecName, String version)
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
    AgentSpecAdminDetail getAgentSpecDetail(String namespaceId, String agentSpecName) throws NacosException;
    
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
    Page<AgentSpecAdminListItem> listAgentSpecs(String namespaceId, String agentSpecName, String search, int pageNo,
            int pageSize) throws NacosException;
    
    /**
     * Upload agentspec from zip file.
     *
     * @param namespaceId namespace ID
     * @param zipBytes zip file bytes
     * @return agentspec name
     * @throws NacosException if upload failed
     */
    default String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
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
    String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite) throws NacosException;
    
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
    Page<AgentSpecBasicInfo> searchAgentSpecs(String namespaceId, String keyword, int pageNo, int pageSize)
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
    AgentSpec queryAgentSpec(String namespaceId, String name, String version, String label) throws NacosException;
    
    /**
     * Create a new draft version based on latest or specified version.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param basedOnVersion base version (optional, default latest)
     * @return created draft version
     * @throws NacosException if draft creation failed
     */
    String createDraft(String namespaceId, String name, String basedOnVersion) throws NacosException;
    
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
     * @param updateLatestLabel whether to update the latest label to this version
     * @throws NacosException if publish failed
     */
    void publish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException;
    
    /**
     * Update labels mapping (label -> version) without changing any version status.
     *
     * @param namespaceId namespace ID
     * @param name agentspec name
     * @param labels label-to-version mapping
     * @throws NacosException if update failed
     */
    void updateLabels(String namespaceId, String name, Map<String, String> labels) throws NacosException;
    
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
    void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online)
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
