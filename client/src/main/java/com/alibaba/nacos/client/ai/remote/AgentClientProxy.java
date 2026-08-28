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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Protocol-neutral client transport surface used by the Agent feature runtime.
 *
 * @author Nacos
 */
public interface AgentClientProxy {
    
    /**
     * Publish one exact Agent definition Version.
     *
     * @param request isolated publication request
     * @return resulting exact Version detail
     * @throws NacosException when the transport request fails
     */
    AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException;
    
    /**
     * Search visible Agent catalog entries.
     *
     * @param request namespace-bound search request
     * @return Agent catalog page
     * @throws NacosException when the transport request fails
     */
    Page<AgentCatalogEntry> searchAgents(AgentSearchRequest request) throws NacosException;
    
    /**
     * Discover one Agent Version and its Endpoint sets.
     *
     * @param request namespace-bound discovery request
     * @return complete discovery result
     * @throws NacosException when the transport request fails
     */
    AgentDiscoveryResult discoverAgent(AgentDiscoveryRequest request) throws NacosException;
    
    /**
     * Replace one complete Agent Endpoint publication.
     *
     * @param batch namespace-bound complete batch
     * @return HTTP liveness settings, or {@code null} for connection-based transports
     * @throws NacosException when the transport request fails
     */
    ClientLivenessInfo registerAgentEndpoints(AgentEndpointRegistrationBatch batch)
        throws NacosException;
    
    /**
     * Remove one complete Agent Endpoint publication.
     *
     * @param namespaceId effective namespace
     * @param agentName Agent name
     * @param protocol protocol token
     * @throws NacosException when the transport request fails
     */
    void deregisterAgentEndpoints(String namespaceId, String agentName, String protocol)
        throws NacosException;
    
    /**
     * Renew HTTP Agent Endpoint publications.
     *
     * @return effective liveness settings
     * @throws NacosException when heartbeat fails or the transport has no heartbeat operation
     */
    ClientLivenessInfo heartbeatAgentEndpoints() throws NacosException;
}
