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

package com.alibaba.nacos.ai.model.skills;

import java.util.Map;

/**
 * Skill list item for admin API response. Contains skill basic info plus governance metadata.
 *
 * @author nacos
 * @since 3.2.0
 */
public class SkillAdminListItem {

    private String namespaceId;

    private String name;

    private String description;

    /**
     * Whether the skill is globally enabled. true=enable, false=disable.
     */
    private boolean enable;

    /**
     * Business tags (JSON string), e.g. ["tag1","tag2"].
     */
    private String bizTags;

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
     * Last update time (epoch millis).
     */
    private Long updateTime;

    /**
     * Total download count across all versions.
     */
    private Long downloadCount;

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
}
