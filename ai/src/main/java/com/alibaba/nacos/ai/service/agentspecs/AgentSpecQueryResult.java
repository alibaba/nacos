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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;

/**
 * Result wrapper returned by the agentspec client query path. Carries the resolved {@link AgentSpec},
 * its published content MD5 and the resolved version string so the controller can populate
 * listener-related response headers without re-parsing metadata.
 *
 * <p>When the client-supplied MD5 matches the published one, {@link #isNotModified()} is
 * {@code true} and {@link #getAgentSpec()} is {@code null}; the controller maps this to HTTP 304
 * without loading or transferring the AgentSpec content.
 *
 * @author nacos
 * @since 3.2.0
 */
public class AgentSpecQueryResult {
    
    private final AgentSpec agentSpec;
    
    private final String md5;
    
    private final String resolvedVersion;
    
    private final boolean notModified;
    
    public AgentSpecQueryResult(AgentSpec agentSpec, String md5, String resolvedVersion) {
        this(agentSpec, md5, resolvedVersion, false);
    }
    
    private AgentSpecQueryResult(AgentSpec agentSpec, String md5, String resolvedVersion,
        boolean notModified) {
        this.agentSpec = agentSpec;
        this.md5 = md5;
        this.resolvedVersion = resolvedVersion;
        this.notModified = notModified;
    }
    
    /**
     * Build a result that signals "client cache is fresh". The {@code agentSpec} payload is
     * intentionally left {@code null} so the controller can short-circuit to HTTP 304 without
     * loading content.
     */
    public static AgentSpecQueryResult notModified(String md5, String resolvedVersion) {
        return new AgentSpecQueryResult(null, md5, resolvedVersion, true);
    }
    
    public AgentSpec getAgentSpec() {
        return agentSpec;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public String getResolvedVersion() {
        return resolvedVersion;
    }
    
    public boolean isNotModified() {
        return notModified;
    }
}
