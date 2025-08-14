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
 *
 */

package com.alibaba.nacos.api.ai.model.a2a;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Objects;

/**
 * AgentCardWrapper.
 *
 * @author KiteSoar
 */
public class AgentCardWrapper {
    
    @JsonUnwrapped
    private AgentCard agentCard;
    
    private String namespaceId;
    
    public AgentCardWrapper(AgentCard agentCard, String namespaceId) {
        this.agentCard = agentCard;
        this.namespaceId = namespaceId;
    }
    
    public AgentCard getAgentCard() {
        return agentCard;
    }
    
    public void setAgentCard(AgentCard agentCard) {
        this.agentCard = agentCard;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentCardWrapper that = (AgentCardWrapper) o;
        return Objects.equals(agentCard, that.agentCard) && Objects.equals(namespaceId, that.namespaceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(agentCard, namespaceId);
    }
}
