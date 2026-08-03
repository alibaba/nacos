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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;

/**
 * Complete definition operations exposed by historical A2A surfaces.
 *
 * @author Nacos
 */
public interface A2aOperationService {
    
    /**
     * Register the first AgentCard version.
     *
     * @param agentCard AgentCard definition
     * @param namespaceId namespace identifier
     * @param registrationType registration type
     * @throws NacosException when registration fails
     */
    void registerAgent(AgentCard agentCard, String namespaceId, String registrationType)
        throws NacosException;
    
    /**
     * Release one AgentCard version from the client surface.
     *
     * @param agentCard AgentCard definition
     * @param namespaceId namespace identifier
     * @param registrationType registration type
     * @param setAsLatest whether to move the latest pointer
     * @throws NacosException when release fails
     */
    void releaseAgent(AgentCard agentCard, String namespaceId, String registrationType,
        boolean setAsLatest) throws NacosException;
    
    /**
     * Update or add one AgentCard version.
     *
     * @param agentCard AgentCard definition
     * @param namespaceId namespace identifier
     * @param registrationType registration type
     * @param setAsLatest whether to move the latest pointer
     * @throws NacosException when update fails
     */
    void updateAgentCard(AgentCard agentCard, String namespaceId, String registrationType,
        boolean setAsLatest) throws NacosException;
    
    /**
     * Delete one AgentCard version or the complete AgentCard definition.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version exact version, or blank for the complete definition
     * @throws NacosException when deletion fails
     */
    void deleteAgent(String namespaceId, String agentName, String version) throws NacosException;
    
    /**
     * Query one AgentCard for a management surface.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version exact version, or blank for latest
     * @param registrationType optional projection type
     * @return AgentCard detail
     * @throws NacosException when query fails
     */
    AgentCardDetailInfo getAgentCard(String namespaceId, String agentName, String version,
        String registrationType) throws NacosException;
    
    /**
     * Query one AgentCard for the client data plane.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version exact version, or blank for latest
     * @param registrationType optional projection type
     * @return AgentCard detail
     * @throws NacosException when query fails
     */
    AgentCardDetailInfo getAgentCardForClient(String namespaceId, String agentName, String version,
        String registrationType) throws NacosException;
    
    /**
     * List AgentCard definitions.
     *
     * @param namespaceId namespace identifier
     * @param agentName optional Agent name filter
     * @param search search mode
     * @param pageNo page number
     * @param pageSize page size
     * @return AgentCard page
     * @throws NacosException when listing fails
     */
    Page<AgentCardVersionInfo> listAgents(String namespaceId, String agentName, String search,
        int pageNo, int pageSize) throws NacosException;
    
    /**
     * List versions of one AgentCard definition.
     *
     * @param namespaceId namespace identifier
     * @param name Agent name
     * @return version details
     * @throws NacosException when listing fails
     */
    List<AgentVersionDetail> listAgentVersions(String namespaceId, String name)
        throws NacosException;
}
