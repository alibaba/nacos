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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Namespace-bound Remote Agent discovery and Endpoint publication service.
 *
 * <p>Default implementations preserve binary compatibility for third-party
 * {@link AiService} implementations compiled before this interface was introduced. Official
 * Nacos clients override the complete surface.</p>
 *
 * @author Nacos
 */
public interface AgentDiscoveryService {
    
    /**
     * Search visible Agent catalog entries.
     *
     * @param request search request
     * @return Agent catalog page
     * @throws NacosException when validation or the remote request fails
     */
    @Since("3.3.0")
    default Page<AgentCatalogEntry> searchAgents(AgentSearchRequest request)
        throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
    
    /**
     * Discover the latest, exact-version, or labeled Agent referenced by {@code reference}.
     *
     * @param reference Agent reference
     * @return complete discovery snapshot
     * @throws NacosException when validation or the remote request fails
     */
    @Since("3.3.0")
    default AgentDiscoveryResult discoverAgent(AgentReference reference) throws NacosException {
        return discoverAgent(reference, null);
    }
    
    /**
     * Discover one Agent and filter its call interfaces and Endpoint sets.
     *
     * @param reference Agent reference
     * @param filter optional discovery filter
     * @return complete filtered discovery snapshot
     * @throws NacosException when validation or the remote request fails
     */
    @Since("3.3.0")
    default AgentDiscoveryResult discoverAgent(AgentReference reference,
        AgentDiscoveryFilter filter) throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
    
    /**
     * Subscribe to one Agent through transport-neutral Watch.
     *
     * @param reference Agent reference
     * @param listener discovery listener
     * @return current snapshot, or {@code null} when the target does not exist yet
     * @throws NacosException when validation or the initial Discover fails
     */
    @Since("3.3.0")
    default AgentDiscoveryResult subscribeAgent(AgentReference reference,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        return subscribeAgent(reference, null, listener);
    }
    
    /**
     * Subscribe to one filtered Agent view through transport-neutral Watch.
     *
     * @param reference Agent reference
     * @param filter optional discovery filter
     * @param listener discovery listener
     * @return current snapshot, or {@code null} when the target does not exist yet
     * @throws NacosException when validation or the initial Discover fails
     */
    @Since("3.3.0")
    default AgentDiscoveryResult subscribeAgent(AgentReference reference,
        AgentDiscoveryFilter filter, AbstractNacosAgentDiscoveryListener listener)
        throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
    
    /**
     * Cancel one local Watch subscription.
     *
     * @param reference Agent reference used to subscribe
     * @param listener listener instance used to subscribe
     * @throws NacosException when validation fails
     */
    @Since("3.3.0")
    default void unsubscribeAgent(AgentReference reference,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        unsubscribeAgent(reference, null, listener);
    }
    
    /**
     * Cancel one local filtered Watch subscription.
     *
     * @param reference Agent reference used to subscribe
     * @param filter discovery filter used to subscribe
     * @param listener listener instance used to subscribe
     * @throws NacosException when validation fails
     */
    @Since("3.3.0")
    default void unsubscribeAgent(AgentReference reference, AgentDiscoveryFilter filter,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
    
    /**
     * Replace this SDK publisher's complete Endpoint Batch for one Agent protocol.
     *
     * @param batch complete Endpoint registration batch
     * @throws NacosException when validation or publication fails
     */
    @Since("3.3.0")
    default void registerAgentEndpoints(AgentEndpointRegistrationBatch batch)
        throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
    
    /**
     * Remove Endpoint natural keys from this SDK publisher's expected complete Batch.
     *
     * @param batch Endpoint deregistration intent
     * @throws NacosException when validation or publication fails
     */
    @Since("3.3.0")
    default void deregisterAgentEndpoints(AgentEndpointDeregistrationBatch batch)
        throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
}
