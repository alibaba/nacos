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

package com.alibaba.nacos.ai.model.agent;

import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;

import java.util.Map;

/**
 * Typed schema-versioned content stored in {@code ai_resource.ext} for an Agent.
 *
 * @author Nacos
 */
public class AgentResourceExt {
    
    public static final int SCHEMA_VERSION = 1;
    
    private Integer schemaVersion;
    
    private String displayName;
    
    private String iconUrl;
    
    private AgentProvider provider;
    
    private Map<String, Object> extensions;
    
    private AgentVersionCatalog versionCatalog;
    
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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
    
    public Map<String, Object> getExtensions() {
        return extensions;
    }
    
    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
    
    public AgentVersionCatalog getVersionCatalog() {
        return versionCatalog;
    }
    
    public void setVersionCatalog(AgentVersionCatalog versionCatalog) {
        this.versionCatalog = versionCatalog;
    }
}
