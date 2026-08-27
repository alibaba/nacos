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

import java.util.Objects;

/**
 * Logical Agent dependency used to map definition and Version changes to projections.
 *
 * @author Nacos
 */
final class AgentLogicalKey {
    
    private final String namespaceId;
    
    private final String agentName;
    
    AgentLogicalKey(String namespaceId, String agentName) {
        this.namespaceId = namespaceId;
        this.agentName = agentName;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentLogicalKey)) {
            return false;
        }
        AgentLogicalKey that = (AgentLogicalKey) other;
        return namespaceId.equals(that.namespaceId) && agentName.equals(that.agentName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, agentName);
    }
}
