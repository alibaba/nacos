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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Maintainer facade for protocol-neutral Agent management.
 *
 * @author Nacos
 */
public interface AgentMaintainerService {
    
    /**
     * Get an Agent overview.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @return Agent overview
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentOverview getAgent(String namespaceId, String agentName) throws NacosException;
    
    /**
     * Get an Agent overview from the default namespace.
     *
     * @param agentName exact Agent name
     * @return Agent overview
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentOverview getAgent(String agentName) throws NacosException {
        return getAgent(Constants.DEFAULT_NAMESPACE_ID, agentName);
    }
    
    /**
     * Update writable Agent metadata.
     *
     * @param namespaceId namespace identifier
     * @param request update request
     * @return updated Agent
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    Agent updateAgent(String namespaceId, AgentUpdateRequest request) throws NacosException;
    
    /**
     * Update Agent metadata in the default namespace.
     *
     * @param request update request
     * @return updated Agent
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default Agent updateAgent(AgentUpdateRequest request) throws NacosException {
        return updateAgent(Constants.DEFAULT_NAMESPACE_ID, request);
    }
    
    /**
     * Delete an Agent definition and all of its Version content.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    void deleteAgent(String namespaceId, String agentName) throws NacosException;
    
    /**
     * Delete an Agent from the default namespace.
     *
     * @param agentName exact Agent name
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default void deleteAgent(String agentName) throws NacosException {
        deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName);
    }
    
    /**
     * List Agent summaries.
     *
     * @param namespaceId namespace identifier
     * @param agentName optional fuzzy name filter
     * @param bizTag optional fuzzy business-tag filter
     * @param scope optional visibility scope
     * @param owner optional owner
     * @param orderBy optional order field; the initial contract accepts {@code download_count}
     * @param pageNo page number
     * @param pageSize page size
     * @return Agent summary page
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize) throws NacosException;
    
    /**
     * List Agent summaries in the default namespace.
     *
     * @param agentName optional fuzzy name filter
     * @param bizTag optional fuzzy business-tag filter
     * @param scope optional visibility scope
     * @param owner optional owner
     * @param orderBy optional order field; the initial contract accepts {@code download_count}
     * @param pageNo page number
     * @param pageSize page size
     * @return Agent summary page
     * @throws NacosException when the request fails
    */
    @Since("3.3.0")
    default Page<AgentSummary> listAgents(String agentName, String bizTag, String scope,
        String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        return listAgents(Constants.DEFAULT_NAMESPACE_ID, agentName, bizTag, scope, owner, orderBy,
            pageNo, pageSize);
    }
    
    /**
     * List Agent Version summaries.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param status optional Version status
     * @param pageNo page number
     * @param pageSize page size
     * @return Version summary page
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    Page<AgentVersionSummary> listAgentVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException;
    
    /**
     * List Agent Version summaries in the default namespace.
     *
     * @param agentName exact Agent name
     * @param status optional Version status
     * @param pageNo page number
     * @param pageSize page size
     * @return Version summary page
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default Page<AgentVersionSummary> listAgentVersions(String agentName, String status, int pageNo,
        int pageSize) throws NacosException {
        return listAgentVersions(Constants.DEFAULT_NAMESPACE_ID, agentName, status, pageNo,
            pageSize);
    }
    
    /**
     * Get one exact Agent Version.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Version
     * @return Agent Version detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionDetail getAgentVersion(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Get one exact Agent Version from the default namespace.
     *
     * @param agentName exact Agent name
     * @param version exact Version
     * @return Agent Version detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionDetail getAgentVersion(String agentName, String version)
        throws NacosException {
        return getAgentVersion(Constants.DEFAULT_NAMESPACE_ID, agentName, version);
    }
    
    /**
     * Get one protocol's complete Runtime Endpoint snapshot.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param protocol protocol token
     * @param version optional Version filter
     * @return Runtime Endpoint snapshot
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    RuntimeEndpointSnapshot getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException;
    
    /**
     * Get a Runtime Endpoint snapshot from the default namespace.
     *
     * @param agentName exact Agent name
     * @param protocol protocol token
     * @param version optional Version filter
     * @return Runtime Endpoint snapshot
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default RuntimeEndpointSnapshot getRuntimeEndpoints(String agentName, String protocol,
        String version) throws NacosException {
        return getRuntimeEndpoints(Constants.DEFAULT_NAMESPACE_ID, agentName, protocol, version);
    }
    
    /**
     * Create an initial or subsequent Agent draft.
     *
     * @param namespaceId namespace identifier
     * @param request draft create request
     * @return created draft
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException;
    
    /**
     * Create an initial or subsequent draft in the default namespace.
     *
     * @param request draft create request
     * @return created draft
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionDetail createDraft(AgentDraftCreateRequest request) throws NacosException {
        return createDraft(Constants.DEFAULT_NAMESPACE_ID, request);
    }
    
    /**
     * Update one exact Agent draft.
     *
     * @param namespaceId namespace identifier
     * @param request draft update request
     * @return updated draft
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException;
    
    /**
     * Update a draft in the default namespace.
     *
     * @param request draft update request
     * @return updated draft
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionDetail updateDraft(AgentDraftUpdateRequest request) throws NacosException {
        return updateDraft(Constants.DEFAULT_NAMESPACE_ID, request);
    }
    
    /**
     * Delete one exact Agent draft.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact draft Version
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    void deleteDraft(String namespaceId, String agentName, String version) throws NacosException;
    
    /**
     * Delete a draft from the default namespace.
     *
     * @param agentName exact Agent name
     * @param version exact draft Version
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default void deleteDraft(String agentName, String version) throws NacosException {
        deleteDraft(Constants.DEFAULT_NAMESPACE_ID, agentName, version);
    }
    
    /**
     * Submit one exact Agent Version.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return resulting Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionSummary submit(String namespaceId, AgentVersionCommand command)
        throws NacosException;
    
    /**
     * Submit one exact Agent Version in the default namespace.
     *
     * @param command exact Version command
     * @return resulting Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionSummary submit(AgentVersionCommand command) throws NacosException {
        return submit(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Publish one exact reviewed Agent Version.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionSummary publish(String namespaceId, AgentVersionCommand command)
        throws NacosException;
    
    /**
     * Publish one exact reviewed Agent Version in the default namespace.
     *
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionSummary publish(AgentVersionCommand command) throws NacosException {
        return publish(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Force-publish one exact Agent Version.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionSummary forcePublish(String namespaceId, AgentVersionCommand command)
        throws NacosException;
    
    /**
     * Force-publish one exact Agent Version in the default namespace.
     *
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionSummary forcePublish(AgentVersionCommand command) throws NacosException {
        return forcePublish(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Move one reviewed Agent Version back to draft.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return draft Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionSummary redraft(String namespaceId, AgentVersionCommand command)
        throws NacosException;
    
    /**
     * Move one reviewed Agent Version back to draft in the default namespace.
     *
     * @param command exact Version command
     * @return draft Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionSummary redraft(AgentVersionCommand command) throws NacosException {
        return redraft(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Bring one offline Agent Version online.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionSummary online(String namespaceId, AgentVersionCommand command)
        throws NacosException;
    
    /**
     * Bring one offline Agent Version online in the default namespace.
     *
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionSummary online(AgentVersionCommand command) throws NacosException {
        return online(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Take one online Agent Version offline.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return offline Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    AgentVersionSummary offline(String namespaceId, AgentVersionCommand command)
        throws NacosException;
    
    /**
     * Take one online Agent Version offline in the default namespace.
     *
     * @param command exact Version command
     * @return offline Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default AgentVersionSummary offline(AgentVersionCommand command) throws NacosException {
        return offline(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Replace custom Agent labels.
     *
     * @param namespaceId namespace identifier
     * @param request labels update request
     * @return updated Agent
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException;
    
    /**
     * Replace custom Agent labels in the default namespace.
     *
     * @param request labels update request
     * @return updated Agent
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default Agent updateLabels(AgentLabelsUpdateRequest request) throws NacosException {
        return updateLabels(Constants.DEFAULT_NAMESPACE_ID, request);
    }
}
