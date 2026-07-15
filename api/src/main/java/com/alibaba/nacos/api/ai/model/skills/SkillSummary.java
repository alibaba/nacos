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

package com.alibaba.nacos.api.ai.model.skills;

import java.util.Map;

/**
 * Skill summary for admin list response.
 * Contains skill basic info plus governance metadata.
 *
 * @author nacos
 */
public class SkillSummary extends SkillBasicInfo {
    
    /**
     * Owner of the skill resource.
     */
    private String owner;
    
    /**
     * Whether the skill is globally enabled. true=enable, false=disable.
     */
    private boolean enable;
    
    /**
     * Business tags (JSON string), e.g. ["tag1","tag2"].
     */
    private String bizTags;
    
    /**
     * Source marker for IP attribution (e.g. local/import/sync).
     */
    private String from;
    
    /**
     * Visibility scope of skill metadata, e.g. PUBLIC or PRIVATE.
     */
    private String scope;
    
    /**
     * Label -> version mapping, e.g. {"latest":"v3","stable":"v2"}.
     */
    private Map<String, String> labels;
    
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
     * Total download count across all versions.
     */
    private Long downloadCount;
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public boolean isEnable() {
        return enable;
    }
    
    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    
    public String getBizTags() {
        return bizTags;
    }
    
    public void setBizTags(String bizTags) {
        this.bizTags = bizTags;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
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
    
    public Long getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
}
