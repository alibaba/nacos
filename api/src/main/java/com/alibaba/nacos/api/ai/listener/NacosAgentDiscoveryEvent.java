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
    
    private final AgentDiscoveryResult agentDiscoveryResult;
    
    /**
     * Create one complete replacement event.
     *
     * @param agentDiscoveryResult complete discovery snapshot
     */
    public NacosAgentDiscoveryEvent(AgentDiscoveryResult agentDiscoveryResult) {
        this.agentDiscoveryResult = agentDiscoveryResult;
    }
    
    /**
     * Get the complete discovery snapshot.
     *
     * @return discovery snapshot
     */
    public AgentDiscoveryResult getAgentDiscoveryResult() {
        return agentDiscoveryResult;
    }
}
