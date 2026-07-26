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

package com.alibaba.nacos.api.ai.model.prompt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt meta summary for prompt list response.
 *
 * @author nacos
 */
public class PromptMetaSummary implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private int schemaVersion = 1;
    
    private String promptKey;
    
    private String description;
    
    private List<String> bizTags = new ArrayList<>();
    
    private String bizTagsStr;
    
    private String latestVersion;
    
    private Long gmtModified;
    
    /**
     * The version currently being edited (draft).
     */
    private String editingVersion;
    
    /**
     * The version currently under pipeline review.
     */
    private String reviewingVersion;
    
    /**
     * Number of online versions.
     */
    private Integer onlineCnt;
    
    /**
     * Label to version mapping, e.g. {"latest":"1.0.0","stable":"0.9.0"}.
     */
    private Map<String, String> labels;
    
    /**
     * Total download count of this prompt (sum of all versions).
     */
    private Long downloadCount;
    
    public int getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
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
    
    public String getBizTagsStr() {
        return bizTagsStr;
    }
    
    public void setBizTagsStr(String bizTagsStr) {
        this.bizTagsStr = bizTagsStr;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }
    
    public Long getGmtModified() {
        return gmtModified;
    }
    
    public void setGmtModified(Long gmtModified) {
        this.gmtModified = gmtModified;
    }
    
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
    
    public Integer getOnlineCnt() {
        return onlineCnt;
    }
    
    public void setOnlineCnt(Integer onlineCnt) {
        this.onlineCnt = onlineCnt;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public Long getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
}
