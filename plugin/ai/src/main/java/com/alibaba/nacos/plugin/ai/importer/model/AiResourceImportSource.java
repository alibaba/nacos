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

package com.alibaba.nacos.plugin.ai.importer.model;

import java.util.List;
import java.util.Map;

/**
 * Runtime import source resolved from operator-owned configuration.
 *
 * <p>This model may contain source endpoint and secret references for importer runtime use, but
 * must not be returned to end users directly.</p>
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportSource {
    
    private String sourceId;
    
    private String displayName;
    
    private String description;
    
    private String pluginName;
    
    private List<String> resourceTypes;
    
    private String endpoint;
    
    private boolean enabled;
    
    private String authRef;
    
    private int connectTimeoutMillis;
    
    private int readTimeoutMillis;
    
    private int maxPageCount;
    
    private int maxItemCount;
    
    private long maxArtifactSize;
    
    private Map<String, String> properties;
    
    public String getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
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
    
    public String getPluginName() {
        return pluginName;
    }
    
    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }
    
    public List<String> getResourceTypes() {
        return resourceTypes;
    }
    
    public void setResourceTypes(List<String> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getAuthRef() {
        return authRef;
    }
    
    public void setAuthRef(String authRef) {
        this.authRef = authRef;
    }
    
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }
    
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }
    
    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }
    
    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }
    
    public int getMaxPageCount() {
        return maxPageCount;
    }
    
    public void setMaxPageCount(int maxPageCount) {
        this.maxPageCount = maxPageCount;
    }
    
    public int getMaxItemCount() {
        return maxItemCount;
    }
    
    public void setMaxItemCount(int maxItemCount) {
        this.maxItemCount = maxItemCount;
    }
    
    public long getMaxArtifactSize() {
        return maxArtifactSize;
    }
    
    public void setMaxArtifactSize(long maxArtifactSize) {
        this.maxArtifactSize = maxArtifactSize;
    }
    
    public Map<String, String> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
