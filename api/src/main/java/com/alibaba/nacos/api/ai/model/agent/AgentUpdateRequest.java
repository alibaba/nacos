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

package com.alibaba.nacos.api.ai.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Complete replacement of the writable fields of one Agent.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentUpdateRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String agentName;
    
    private String displayName;
    
    private String description;
    
    private String iconUrl;
    
    private AgentProvider provider;
    
    private List<String> tags;
    
    private Map<String, Object> extensions;
    
    private String status;
    
    /**
     * Validate required request identity and status.
     */
    public void validate() {
        AgentAdminRequestUtils.validateIdentity(agentName);
        AgentAdminRequestUtils.validateWritableStatus(status);
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
    
    public AgentProvider getProvider() {
        return provider;
    }
    
    public void setProvider(AgentProvider provider) {
        this.provider = provider;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public Map<String, Object> getExtensions() {
        return extensions;
    }
    
    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
