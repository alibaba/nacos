/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.common.notify.Event;

/**
 * Nacos AI module agent spec changed event in nacos-client.
 *
 * @author nacos
 */
public class AgentSpecChangedEvent extends Event {
    
    private static final long serialVersionUID = 7893214560182347651L;
    
    private final String agentSpecName;
    
    private final AgentSpec agentSpec;
    
    public AgentSpecChangedEvent(String agentSpecName, AgentSpec agentSpec) {
        this.agentSpecName = agentSpecName;
        this.agentSpec = agentSpec;
    }
    
    public String getAgentSpecName() {
        return agentSpecName;
    }
    
    public AgentSpec getAgentSpec() {
        return agentSpec;
    }
}
