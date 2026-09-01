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

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;

/**
 * Immutable materialized state supplied to one Agent Watch transport.
 *
 * @author Nacos
 */
public final class AgentWatchRegistration {
    
    private final String clientWatchId;
    
    private final AgentDiscoveryRequest discoveryRequest;
    
    private final String materializedFingerprint;
    
    AgentWatchRegistration(String clientWatchId, AgentDiscoveryRequest discoveryRequest,
        String materializedFingerprint) {
        this.clientWatchId = clientWatchId;
        this.discoveryRequest =
            AgentDiscoveryCanonicalizer.canonicalizeRequest(discoveryRequest);
        this.materializedFingerprint = materializedFingerprint;
    }
    
    /**
     * Get the process-stable client Watch identifier.
     *
     * @return client Watch identifier
     */
    public String getClientWatchId() {
        return clientWatchId;
    }
    
    /**
     * Get an isolated canonical Discover request.
     *
     * @return canonical Discover request
     */
    public AgentDiscoveryRequest getDiscoveryRequest() {
        return AgentDiscoveryCanonicalizer.canonicalizeRequest(discoveryRequest);
    }
    
    /**
     * Get the last materialized complete-result fingerprint.
     *
     * @return fingerprint, or {@code null} before a snapshot exists
     */
    public String getMaterializedFingerprint() {
        return materializedFingerprint;
    }
}
