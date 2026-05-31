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

package com.alibaba.nacos.ai.config;

import java.util.List;
import java.util.Map;

/**
 * Unified legacy prompt data for migration. Holds prompt metadata and version list
 * (without version content, which is loaded on demand via
 * {@link PromptLegacyDataReader#readVersionContent}).
 *
 * @author nacos
 * @since 3.2.0
 */
public class LegacyPromptData {
    
    private String namespaceId;
    
    private String promptKey;
    
    private String description;
    
    private List<String> bizTags;
    
    private Map<String, String> labels;
    
    private String latestVersion;
    
    private List<String> versions;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getPromptKey() {
        return promptKey;
    }
    
    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getBizTags() {
        return bizTags;
    }
    
    public void setBizTags(List<String> bizTags) {
        this.bizTags = bizTags;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }
    
    public List<String> getVersions() {
        return versions;
    }
    
    public void setVersions(List<String> versions) {
        this.versions = versions;
    }
}
