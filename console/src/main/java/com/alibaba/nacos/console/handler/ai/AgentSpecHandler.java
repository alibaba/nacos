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

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;

/**
 * AgentSpec handler.
 *
 * @author nacos
 */
public interface AgentSpecHandler {
    
    /**
     * Get agentspec.
     *
     * @param form agentspec form
     * @return agentspec admin detail
     * @throws NacosException nacos exception
     */
    AgentSpecMeta getAgentSpec(AgentSpecForm form) throws NacosException;
    
    /**
     * Get agentspec version detail. Returns full agentspec content for a specific version.
     *
     * @param form agentspec form (with version)
     * @return full agentspec content
     * @throws NacosException nacos exception
     */
    AgentSpec getAgentSpecVersion(AgentSpecForm form) throws NacosException;
    
    /**
     * Delete agentspec.
     *
     * @param form agentspec form
     * @throws NacosException nacos exception
     */
    void deleteAgentSpec(AgentSpecForm form) throws NacosException;
    
    /**
     * List agentspecs.
     *
     * @param agentSpecListForm agentspec list form
     * @param pageForm page form
     * @return agentspec list
     * @throws NacosException nacos exception
     */
    Page<AgentSpecSummary> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException;
    
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
     * Create draft version based on latest or a specified version.
     *
     * @param form draft create form
     * @return created draft version
     * @throws NacosException if operation failed
     */
    String createDraft(AgentSpecDraftCreateForm form) throws NacosException;
    
    /**
     * Update current draft content.
     *
     * @param form update form
     * @throws NacosException if operation failed
     */
    void updateDraft(AgentSpecUpdateForm form) throws NacosException;
    
    /**
     * Delete current draft version.
     *
     * @param form agentspec form
     * @throws NacosException if operation failed
     */
    void deleteDraft(AgentSpecForm form) throws NacosException;
    
    /**
     * Submit a version for pipeline review.
     *
     * @param form submit form
     * @return submit result (e.g. pipeline id)
     * @throws NacosException if operation failed
     */
    String submit(AgentSpecSubmitForm form) throws NacosException;
    
    /**
     * Publish an approved reviewing version.
     *
     * @param form publish form
     * @throws NacosException if operation failed
     */
    void publish(AgentSpecPublishForm form) throws NacosException;
    
    /**
     * Force-publish a version, bypassing pipeline validation.
     *
     * @param form publish form
     * @throws NacosException if operation failed
     */
    void forcePublish(AgentSpecPublishForm form) throws NacosException;
    
    /**
     * Re-edit a reviewed version, transitioning it back to draft status.
     *
     * @param form publish form (contains namespace, agentspec name, version)
     * @throws NacosException if operation failed
     */
    void redraft(AgentSpecPublishForm form) throws NacosException;
    
    /**
     * Update runtime route labels without changing version status.
     *
     * @param form labels update form
     * @throws NacosException if operation failed
     */
    void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException;
    
    /**
     * Update agentspec biz tags without changing version status.
     *
     * @param form biz tags update form
     * @throws NacosException if operation failed
     */
    void updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException;
    
    /**
     * Change online/offline status.
     *
     * @param form online form
     * @param online true for online, false for offline
     * @throws NacosException if operation failed
     */
    void changeOnlineStatus(AgentSpecOnlineForm form, boolean online) throws NacosException;
    
    /**
     * Update agentspec visibility scope.
     *
     * @param form scope update form
     * @throws NacosException if operation failed
     */
    void updateScope(AgentSpecScopeForm form) throws NacosException;
}
