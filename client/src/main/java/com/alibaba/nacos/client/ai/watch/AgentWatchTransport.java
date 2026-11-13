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

package com.alibaba.nacos.client.ai.watch;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.lifecycle.Closeable;

/**
 * Observation lifecycle owned by one Agent Watch transport.
 *
 * <p>The three implementations obtain change signals differently:</p>
 * <ul>
 * <li>{@link GrpcAgentWatchTransport} subscribes on the shared gRPC connection and receives
 * server hints that trigger Discover when a refresh is needed.</li>
 * <li>{@link HttpAgentWatchTransport} sends batch long-poll requests; the server returns
 * changed Watch identifiers or an unchanged timeout, rather than complete snapshots.</li>
 * <li>{@link PollingAgentWatchTransport} periodically triggers ordinary Discover as a
 * fallback, without establishing a server-side Watch.</li>
 * </ul>
 *
 * <p>None owns a business snapshot or user Listener. Each receives immutable Watch
 * registrations and reports internal transport signals to {@link AgentWatchManager}.
 * {@link AgentWatchTransportRouter} selects one owner per Watch in steady state. During a
 * handover, the new binding may start before the previous one stops; already-triggered
 * refreshes may finish through the same manager and fingerprint comparison.</p>
 *
 * @author Nacos
 */
public interface AgentWatchTransport extends Closeable {
    
    /**
     * Start observing one canonical Watch.
     *
     * @param registration materialized Watch state
     * @param callback transport-neutral signal callback
     * @throws NacosException when activation is rejected
     */
    void start(AgentWatchRegistration registration, AgentWatchTransportCallback callback)
        throws NacosException;
    
    /**
     * Update the materialized fingerprint of an active Watch.
     *
     * @param registration current materialized state
     */
    void update(AgentWatchRegistration registration);
    
    /**
     * Stop observing one Watch.
     *
     * @param clientWatchId client Watch identifier
     */
    void stop(String clientWatchId);
    
    /**
     * Stop all observation work owned by this transport.
     */
    @Override
    void shutdown();
}
