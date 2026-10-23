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

import java.util.List;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
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
    default Page<AgentSummary> searchAgents(AgentSearchRequest request)
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
     * <p>The listener receives the initial complete state and subsequent changes. The first
     * callback may run before or after this method returns; prepare listener state beforehand.
     * Repeating the same reference, filter and listener does not repeat initialization.
     *
     * @param reference Agent reference
     * @param listener discovery listener
     * @throws NacosException when validation, initial Discover, or synchronous Watch admission fails
     */
    @Since("3.3.0")
    default void subscribeAgent(AgentReference reference,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        subscribeAgent(reference, null, listener);
    }
    
    /**
     * Subscribe to one filtered Agent view through transport-neutral Watch.
     *
     * <p>A new listener receives the current complete state even when joining an existing
     * Watch. An initially absent target produces an UNAVAILABLE event and remains pending.
     * Accepted subscriptions remain until unsubscribe or service shutdown. Background failures
     * retry internally without data callbacks. UNAVAILABLE means authorized Discover confirmed
     * the requested Agent reference does not exist; empty or unhealthy endpoints remain snapshots.
     * HTTP Watch admission is asynchronous: quota failures retain accepted additions and retry
     * while observing the acknowledged set. Further growth during known exhaustion is rejected
     * synchronously. Joining an existing Watch does not count as remote growth.
     * Initial state and changes use the same serialized callback channel; an initialization
     * superseded by a newer notification is not sent afterwards. Cancellation and shutdown
     * invalidate queued notifications. Intermediate snapshots and brief UNAVAILABLE states may
     * coalesce: each listener eventually receives the latest complete state when execution is
     * available. Executor rejection and getExecutor failures retain that state for local retry,
     * including initialization, without another server change. A callback that has already
     * started and throws is logged but not automatically replayed. Pending delivery memory
     * does not grow with the number of changes. Bound blocking I/O in your callback with timeouts.
     *
     * @param reference Agent reference
     * @param filter optional discovery filter
     * @param listener discovery listener
     * @throws NacosException when validation, initial Discover, or synchronous Watch admission fails
     */
    @Since("3.3.0")
    default void subscribeAgent(AgentReference reference,
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
     * <p>Include every Endpoint to retain for this Agent and protocol. Omitted addresses from
     * this publisher are removed; other publishers and protocols are unaffected. To replace an
     * address, register the desired complete Batch without subsequently deregistering the old one.</p>
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
     * @param agentName Agent name
     * @param protocol Agent protocol
     * @param endpoints natural keys to remove
     * @throws NacosException when validation or publication fails
     */
    @Since("3.3.0")
    default void deregisterAgentEndpoints(String agentName, String protocol,
        List<Endpoint> endpoints)
        throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent discovery is not implemented by this AiService.");
    }
}
