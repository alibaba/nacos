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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;

import java.util.Objects;

/**
 * Protocol-neutral identity of one shared server-side Agent Discover projection.
 *
 * <p>Owner, visibility, connection, and listener identity are deliberately excluded. Those are
 * Watch admission facts rather than public projection identity.</p>
 *
 * @author Nacos
 */
public final class AgentProjectionKey implements Comparable<AgentProjectionKey> {
    
    private final String value;
    
    private final AgentDiscoveryRequest request;
    
    private AgentProjectionKey(AgentDiscoveryRequest request) {
        this.request = AgentDiscoveryCanonicalizer.canonicalizeRequest(request);
        this.value = AgentDiscoveryCanonicalizer.canonicalRequestKey(this.request);
    }
    
    /**
     * Build a key from one Discover request.
     *
     * @param request Discover request
     * @return canonical projection key
     */
    public static AgentProjectionKey of(AgentDiscoveryRequest request) {
        return new AgentProjectionKey(request);
    }
    
    public String getValue() {
        return value;
    }
    
    public String getNamespaceId() {
        return request.getNamespaceId();
    }
    
    public String getAgentName() {
        return request.getReference().getAgentName();
    }
    
    /**
     * Return a defensive request copy suitable for current-fact projection.
     *
     * @return canonical request copy
     */
    public AgentDiscoveryRequest getRequest() {
        return AgentDiscoveryCanonicalizer.canonicalizeRequest(request);
    }
    
    @Override
    public int compareTo(AgentProjectionKey other) {
        return value.compareTo(other.value);
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentProjectionKey)) {
            return false;
        }
        AgentProjectionKey that = (AgentProjectionKey) other;
        return value.equals(that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}
