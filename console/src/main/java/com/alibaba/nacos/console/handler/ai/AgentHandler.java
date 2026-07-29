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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Handler for protocol-neutral Agent Console operations.
 *
 * @author Nacos
 */
public interface AgentHandler {
    
    /**
     * Get one Agent overview.
     */
    AgentOverview getAgent(String namespaceId, String agentName) throws NacosException;
    
    /**
     * Replace writable Agent metadata.
     */
    Agent updateAgent(String namespaceId, AgentUpdateRequest request) throws NacosException;
    
    /**
     * Delete one Agent definition.
     */
    void deleteAgent(String namespaceId, String agentName) throws NacosException;
    
    /**
     * List Agent summaries.
     */
    Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException;
    
    /**
     * List Agent Version summaries.
     */
    Page<AgentVersionSummary> listVersions(String namespaceId, String agentName, String status,
        int pageNo, int pageSize) throws NacosException;
    
    /**
     * Get one exact Agent Version.
     */
    AgentVersionDetail getVersion(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Get one protocol's complete Runtime Endpoint snapshot.
     */
    RuntimeEndpointSnapshot getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException;
    
    /**
     * Create one initial or subsequent Agent draft.
     */
    AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException;
    
    /**
     * Replace one exact Agent draft.
     */
    AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException;
    
    /**
     * Delete one exact Agent draft.
     */
    void deleteDraft(String namespaceId, String agentName, String version) throws NacosException;
    
    /**
     * Submit one exact Agent Version.
     */
    AgentVersionSummary submit(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Publish one exact reviewed Agent Version.
     */
    AgentVersionSummary publish(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Force-publish one exact Agent Version.
     */
    AgentVersionSummary forcePublish(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Move one exact reviewed Agent Version back to draft.
     */
    AgentVersionSummary redraft(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Bring one exact offline Agent Version online.
     */
    AgentVersionSummary online(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Take one exact online Agent Version offline.
     */
    AgentVersionSummary offline(String namespaceId, String agentName, String version)
        throws NacosException;
    
    /**
     * Replace custom Agent labels.
     */
    Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException;
}
