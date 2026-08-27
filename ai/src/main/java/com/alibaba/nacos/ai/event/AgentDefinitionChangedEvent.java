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

package com.alibaba.nacos.ai.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * Current Agent definition, Version, label, status, or visibility facts changed.
 *
 * @author Nacos
 */
public class AgentDefinitionChangedEvent extends Event {
    
    private static final long serialVersionUID = 7444736129962946422L;
    
    private final String namespaceId;
    
    private final String agentName;
    
    public AgentDefinitionChangedEvent(String namespaceId, String agentName) {
        this.namespaceId = namespaceId;
        this.agentName = agentName;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getAgentName() {
        return agentName;
    }
}
