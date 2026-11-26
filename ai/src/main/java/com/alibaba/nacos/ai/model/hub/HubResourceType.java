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

package com.alibaba.nacos.ai.model.hub;

/**
 * Closed set of resource identities supported by Hub reference persistence.
 * Content, authorization and serving remain the responsibility of each resource adapter.
 */
public enum HubResourceType {
    
    SKILL("skill"),
    MCP("mcp"),
    AGENT("agent"),
    PROMPT("prompt"),
    AGENTSPEC("agentspec");
    
    private final String resourceType;
    
    HubResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    /** Return the exact persisted AI resource type. */
    public String resourceType() {
        return resourceType;
    }
    
    /** Reject unknown types rather than turning Hub storage into a generic row accessor. */
    public static HubResourceType fromResourceType(String resourceType) {
        for (HubResourceType type : values()) {
            if (type.resourceType.equals(resourceType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported Hub resource type: " + resourceType);
    }
}
