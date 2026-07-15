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

package com.alibaba.nacos.api.ai.model.agentspecs;

import java.util.Map;

/**
 * AgentSpec resource structure for files within the worker package.
 * Each zip entry (except manifest.json) corresponds to an AgentSpecResource.
 *
 * @author nacos
 */
public class AgentSpecResource {
    
    /**
     * Resource name (includes file path, e.g., config/SOUL.md).
     */
    private String name;
    
    /**
     * Resource type: config, skill, cron, dockerfile, other.
     */
    private String type;
    
    /**
     * Resource content (string format, binary files Base64 encoded).
     */
    private String content;
    
    /**
     * Resource metadata (optional).
     */
    private Map<String, Object> metadata;
    
    /**
     * Get resource unique identifier.
     * Format: "type::name" if type is not blank, otherwise "name".
     * The separator "::" is used because it's not in the allowed character set for type and name.
     *
     * @return resource unique identifier
     */
    public String getResourceIdentifier() {
        if (type != null && !type.trim().isEmpty()) {
            return type + "::" + name;
        }
        return name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
