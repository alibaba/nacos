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
 *
 */

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * A2A maintainer service interface.
 *
 * @author nacos
 */
public interface A2aMaintainerService {

    /**
     * Register agent.
     *
     * @param agentCard the agent card detail to register
     * @return true if the agent is registered successfully, false otherwise
     * @throws NacosException if the agent registration fails due to invalid input or internal error
     */
    default boolean registerAgent(AgentCard agentCard) throws NacosException {
        return registerAgent(agentCard, AiConstants.A2a.A2A_DEFAULT_NAMESPACE);
    }
    
    /**
     * Register agent.
     *
     * @param agentCard the agent card detail to register
     * @param namespaceId the namespace id
     * @return true if the agent is registered successfully, false otherwise
     * @throws NacosException if the agent registration fails due to invalid input or internal error
     */
    boolean registerAgent(AgentCard agentCard, String namespaceId) throws NacosException;

    /**
     * Get agent card with versions.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @param registrationType the registration type
     * @return agent card with versions
     * @throws NacosException if the agent get fails due to invalid input or internal error
     */
    AgentCardVersionInfo getAgentCardWithVersions(String agentName, String namespaceId, String registrationType) throws NacosException;
    
    /**
     * Get agent card with versions.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @return agent card with versions
     * @throws NacosException if the agent get fails due to invalid input or internal error
     */
    default AgentCardVersionInfo getAgentCardWithVersions(String agentName, String namespaceId) throws NacosException {
        return getAgentCardWithVersions(agentName, namespaceId, AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
    }
    
    /**
     * Get agent card.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @return agent card
     * @throws NacosException if the agent get fails due to invalid input or internal error
     */
    default AgentCard getAgentCard(String agentName, String namespaceId) throws NacosException {
        return getAgentCardWithVersions(agentName, namespaceId);
    }
    
    /**
     * Update agent card.
     *
     * @param agentCard the agent card detail to update
     * @param namespaceId the namespace id
     * @return true if the agent is updated successfully, false otherwise
     * @throws NacosException if the agent update fails due to invalid input or internal error
     */
    boolean updateAgentCard(AgentCard agentCard, String namespaceId) throws NacosException;
    
    /**
     * Delete agent.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @return true if the agent is deleted successfully, false otherwise
     * @throws NacosException if the agent delete fails due to invalid input or internal error
     */
    boolean deleteAgent(String agentName, String namespaceId) throws NacosException;
}
