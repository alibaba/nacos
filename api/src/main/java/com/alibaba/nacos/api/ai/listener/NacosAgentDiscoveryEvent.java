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

package com.alibaba.nacos.api.ai.listener;

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;

/**
 * Complete Remote Agent discovery replacement event.
 *
 * @author Nacos
 */
public class NacosAgentDiscoveryEvent implements NacosAiEvent {
    
    private final NacosAgentDiscoveryEventType type;
    
    private final AgentDiscoveryResult agentDiscoveryResult;
    
    private final Integer errorCode;
    
    private final String errorMessage;
    
    /**
     * Create one complete replacement event.
     *
     * @param agentDiscoveryResult complete discovery snapshot
     */
    public NacosAgentDiscoveryEvent(AgentDiscoveryResult agentDiscoveryResult) {
        this.type = NacosAgentDiscoveryEventType.SNAPSHOT;
        this.agentDiscoveryResult = agentDiscoveryResult;
        this.errorCode = null;
        this.errorMessage = null;
    }
    
    private NacosAgentDiscoveryEvent(int errorCode, String errorMessage) {
        this.type = NacosAgentDiscoveryEventType.UNAVAILABLE;
        this.agentDiscoveryResult = null;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Create an unavailable transition without exposing a stale discovery result.
     *
     * @param errorCode Nacos error code
     * @param errorMessage error description
     * @return unavailable event
     */
    public static NacosAgentDiscoveryEvent unavailable(int errorCode, String errorMessage) {
        return new NacosAgentDiscoveryEvent(errorCode, errorMessage);
    }
    
    /**
     * Get the event type.
     *
     * @return event type
     */
    public NacosAgentDiscoveryEventType getType() {
        return type;
    }
    
    /**
     * Get the complete discovery snapshot.
     *
     * @return discovery snapshot
     */
    public AgentDiscoveryResult getAgentDiscoveryResult() {
        return agentDiscoveryResult;
    }
    
    /**
     * Get the Nacos error code for an unavailable event.
     *
     * @return error code, or {@code null} for a snapshot
     */
    public Integer getErrorCode() {
        return errorCode;
    }
    
    /**
     * Get the error description for an unavailable event.
     *
     * @return error description, or {@code null} for a snapshot
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
