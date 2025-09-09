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
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.List;

/**
 * A2A maintainer service interface.
 *
 * @author nacos
 */
public interface A2aMaintainerService {
    
    /**
     * Register agent to default namespace.
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
    default boolean registerAgent(AgentCard agentCard, String namespaceId) throws NacosException {
        return registerAgent(agentCard, namespaceId, AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
    }
    
    /**
     * Register agent.
     *
     * @param agentCard the agent card detail to register
     * @param namespaceId the namespace id
     * @param registrationType {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE}
     * @return true if the agent is registered successfully, false otherwise
     * @throws NacosException if the agent registration fails due to invalid input or internal error
     */
    boolean registerAgent(AgentCard agentCard, String namespaceId, String registrationType) throws NacosException;
    
    /**
     * Get agent card from default namespace.
     *
     * @param agentName   the agent name
     * @return agent card
     * @throws NacosException if the agent get fails due to invalid input or internal error
     */
    default AgentCardDetailInfo getAgentCard(String agentName) throws NacosException {
        return getAgentCard(agentName, AiConstants.A2a.A2A_DEFAULT_NAMESPACE);
    }
    
    /**
     * Get agent card.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @return agent card
     * @throws NacosException if the agent get fails due to invalid input or internal error
     */
    default AgentCardDetailInfo getAgentCard(String agentName, String namespaceId) throws NacosException {
        return getAgentCard(agentName, namespaceId, StringUtils.EMPTY);
    }
    
    /**
     * Get agent card.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @param registrationType {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE}
     * @return agent card
     * @throws NacosException if the agent get fails due to invalid input or internal error
     */
    AgentCardDetailInfo getAgentCard(String agentName, String namespaceId, String registrationType)
            throws NacosException;
    
    /**
     * Update agent card in default namespace.
     *
     * @param agentCard the agent card detail to update
     * @return true if the agent is updated successfully, false otherwise
     * @throws NacosException if the agent update fails due to invalid input or internal error
     */
    default boolean updateAgentCard(AgentCard agentCard) throws NacosException {
        return updateAgentCard(agentCard, AiConstants.A2a.A2A_DEFAULT_NAMESPACE);
    }
    
    /**
     * Update agent card and set as latest version.
     *
     * @param agentCard the agent card detail to update
     * @param namespaceId the namespace id
     * @return true if the agent is updated successfully, false otherwise
     * @throws NacosException if the agent update fails due to invalid input or internal error
     */
    default boolean updateAgentCard(AgentCard agentCard, String namespaceId) throws NacosException {
        return updateAgentCard(agentCard, namespaceId, true);
    }
    
    /**
     * Update agent card.
     *
     * @param agentCard the agent card detail to update
     * @param namespaceId the namespace id
     * @param setAsLatest whether set as latest version
     * @return true if the agent is updated successfully, false otherwise
     * @throws NacosException if the agent update fails due to invalid input or internal error
     */
    default boolean updateAgentCard(AgentCard agentCard, String namespaceId, boolean setAsLatest)
            throws NacosException {
        return updateAgentCard(agentCard, namespaceId, setAsLatest, StringUtils.EMPTY);
    }
    
    /**
     * Update agent card.
     *
     * @param agentCard         the agent card detail to update
     * @param namespaceId       the namespace id
     * @param setAsLatest       whether set as latest version
     * @param registrationType  {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE}
     * @return true if the agent is updated successfully, false otherwise
     * @throws NacosException if the agent update fails due to invalid input or internal error
     */
    boolean updateAgentCard(AgentCard agentCard, String namespaceId, boolean setAsLatest, String registrationType)
            throws NacosException;
    
    /**
     * Delete agent from default namespace.
     *
     * @param agentName   the agent name
     * @return true if the agent is deleted successfully, false otherwise
     * @throws NacosException if the agent delete fails due to invalid input or internal error
     */
    default boolean deleteAgent(String agentName) throws NacosException {
        return deleteAgent(agentName, AiConstants.A2a.A2A_DEFAULT_NAMESPACE);
    }
    
    /**
     * Delete agent.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @return true if the agent is deleted successfully, false otherwise
     * @throws NacosException if the agent delete fails due to invalid input or internal error
     */
    default boolean deleteAgent(String agentName, String namespaceId) throws NacosException {
        return deleteAgent(agentName, namespaceId, StringUtils.EMPTY);
    }
    
    /**
     * Delete agent target version.
     *
     * @param agentName   the agent name
     * @param namespaceId the namespace id
     * @param version     the version of agent card, if empty or null, delete all agent versions
     * @return true if the agent is deleted successfully, false otherwise
     * @throws NacosException if the agent delete fails due to invalid input or internal error
     */
    boolean deleteAgent(String agentName, String namespaceId, String version) throws NacosException;
    
    /**
     * List all versions for target agent.
     *
     * @param agentName agent name
     * @return list of agent versions
     * @throws NacosException if the agent version query fails due to invalid input or internal error
     */
    default List<AgentVersionDetail> listAllVersionOfAgent(String agentName) throws NacosException {
        return listAllVersionOfAgent(agentName, AiConstants.A2a.A2A_DEFAULT_NAMESPACE);
    }
    
    /**
     * List all versions for target agent.
     *
     * @param agentName agent name
     * @param namespaceId the namespace id
     * @return list of agent versions
     * @throws NacosException if the agent version query fails due to invalid input or internal error
     */
    List<AgentVersionDetail> listAllVersionOfAgent(String agentName, String namespaceId) throws NacosException;
    
    /**
     * Search agent cards by agent name from default namespace with top 100 results.
     *
     * @param agentNamePattern agent name pattern
     * @return page of agent cards
     * @throws NacosException if the agent search fails due to invalid input or internal error
     */
    default Page<AgentCardVersionInfo> searchAgentCardsByName(String agentNamePattern) throws NacosException {
        return searchAgentCardsByName(agentNamePattern, 1, 100);
    }
    
    /**
     * Search agent cards by agent name from default namespace.
     *
     * @param agentNamePattern  agent name pattern
     * @param pageNo            page number
     * @param pageSize          size per page
     * @return page of agent cards
     * @throws NacosException if the agent search fails due to invalid input or internal error
     */
    default Page<AgentCardVersionInfo> searchAgentCardsByName(String agentNamePattern, int pageNo, int pageSize)
            throws NacosException {
        return searchAgentCardsByName(AiConstants.A2a.A2A_DEFAULT_NAMESPACE, agentNamePattern, pageNo, pageSize);
    }
    
    /**
     * Search agent cards by agent name from target namespace.
     *
     * @param namespaceId       namespace id
     * @param agentNamePattern  agent name pattern
     * @param pageNo            page number
     * @param pageSize          size per page
     * @return page of agent cards
     * @throws NacosException if the agent search fails due to invalid input or internal error
     */
    Page<AgentCardVersionInfo> searchAgentCardsByName(String namespaceId, String agentNamePattern, int pageNo,
            int pageSize) throws NacosException;
    
    /**
     * List agent cards from default namespace with top 100 results.
     *
     * @return page of agent cards
     * @throws NacosException if the agent list fails due to invalid input or internal error
     */
    default Page<AgentCardVersionInfo> listAgentCards() throws NacosException {
        return listAgentCards(1, 100);
    }
    
    /**
     * List agent cards from default namespace.
     *
     * @param pageNo        page number
     * @param pageSize      size per page
     * @return page of agent cards
     * @throws NacosException if the agent list fails due to invalid input or internal error
     */
    default Page<AgentCardVersionInfo> listAgentCards(int pageNo, int pageSize) throws NacosException {
        return listAgentCards(AiConstants.A2a.A2A_DEFAULT_NAMESPACE, pageNo, pageSize);
    }
    
    /**
     * List agent cards from target namespace.
     *
     * @param namespaceId   namespace id
     * @param pageNo        page number
     * @param pageSize      size per page
     * @return page of agent cards
     * @throws NacosException if the agent list fails due to invalid input or internal error
     */
    default Page<AgentCardVersionInfo> listAgentCards(String namespaceId, int pageNo, int pageSize)
            throws NacosException {
        return listAgentCards(namespaceId, StringUtils.EMPTY, pageNo, pageSize);
    }
    
    /**
     * List agent cards by accurate agent name from target namespace.
     *
     * @param namespaceId   namespace id
     * @param agentName     agent name, if empty or null, list all agent cards
     * @param pageNo        page number
     * @param pageSize      size per page
     * @return page of agent cards
     * @throws NacosException if the agent list fails due to invalid input or internal error
     */
    Page<AgentCardVersionInfo> listAgentCards(String namespaceId, String agentName, int pageNo, int pageSize)
            throws NacosException;
}
