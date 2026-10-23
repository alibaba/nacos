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

import java.io.Serializable;
import java.util.Map;
import java.util.List;

/**
 * Agent version lifecycle, complete label mapping and online version summaries.
 *
 * @author Nacos
 */
public class AgentVersionInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String editingVersion;
    
    private String reviewingVersion;
    
    private List<AgentVersionSummary> onlineVersions;
    
    private Map<String, String> labels;
    
    public String getEditingVersion() {
        return editingVersion;
    }
    
    public void setEditingVersion(String editingVersion) {
        this.editingVersion = editingVersion;
    }
    
    public String getReviewingVersion() {
        return reviewingVersion;
    }
    
    public void setReviewingVersion(String reviewingVersion) {
        this.reviewingVersion = reviewingVersion;
    }
    
    public List<AgentVersionSummary> getOnlineVersions() {
        return onlineVersions;
    }
    
    public void setOnlineVersions(List<AgentVersionSummary> onlineVersions) {
        this.onlineVersions = onlineVersions;
    }
    
    /**
     * Get the derived number of online versions, without adding a JSON property.
     *
     * @return online count, or zero when no online versions are available
     */
    public int onlineCnt() {
        return onlineVersions == null ? 0 : onlineVersions.size();
    }
    
    /**
     * Resolve the latest label without maintaining a separate catalog field.
     *
     * @return latest version, or null when no latest label exists
     */
    public String latestVersion() {
        return labels == null ? null : labels.get("latest");
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
