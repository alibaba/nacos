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
 * Wire lifecycle owned by one Agent Watch transport.
 *
 * <p>The transport owns no business snapshot or user Listener. It receives immutable Watch
 * registrations and reports only invalidation or unavailable signals.</p>
 *
 * @author Nacos
 */
public interface AgentWatchTransport extends Closeable {
    
    /**
     * Activate one canonical Wire Intent.
     *
     * @param registration materialized Watch state
     * @param callback transport-neutral signal callback
     * @throws NacosException when activation is rejected
     */
    void start(AgentWatchRegistration registration, AgentWatchTransportCallback callback)
        throws NacosException;
    
    /**
     * Update the materialized fingerprint of an active Wire Intent.
     *
     * @param registration current materialized state
     */
    void update(AgentWatchRegistration registration);
    
    /**
     * Stop one Wire Intent.
     *
     * @param clientWatchId client Watch identifier
     */
    void stop(String clientWatchId);
    
    /**
     * Stop all Wire coordination owned by this transport.
     */
    @Override
    void shutdown();
}
