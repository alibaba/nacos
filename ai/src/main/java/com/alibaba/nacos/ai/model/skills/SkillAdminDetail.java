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

import java.util.List;
import java.util.Map;

/**
 * Skill detail for admin API response. Contains version governance metadata and all version summaries.
 *
 * @author nacos
 * @since 3.2.0
 */
public class SkillAdminDetail {

    /**
     * Whether the skill is enabled globally.
     */
    private boolean enable;

    /**
     * The version currently being edited (draft).
     */
    private String editingVersion;

    /**
     * The version currently under pipeline review.
     */
    private String reviewingVersion;

    /**
     * Label -> version mapping, e.g. {"latest":"v3","stable":"v2"}.
     */
    private Map<String, String> labels;

    /**
     * Number of online versions.
     */
    private Integer onlineCnt;

    /**
     * Last update time (epoch millis).
     */
    private Long updateTime;

    /**
     * All version summaries for this skill.
     */
    private List<SkillVersionSummary> versions;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
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

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
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

    public List<SkillVersionSummary> getVersions() {
        return versions;
    }

    public void setVersions(List<SkillVersionSummary> versions) {
        this.versions = versions;
    }

    /**
     * Summary of a single skill version for admin display.
     */
    public static class SkillVersionSummary {

        private String version;

        private String status;

        private String author;

        private String description;

        private Long createTime;

        private Long updateTime;

        private String publishPipelineInfo;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(Long createTime) {
            this.createTime = createTime;
        }

        public Long getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(Long updateTime) {
            this.updateTime = updateTime;
        }

        public String getPublishPipelineInfo() {
            return publishPipelineInfo;
        }

        public void setPublishPipelineInfo(String publishPipelineInfo) {
            this.publishPipelineInfo = publishPipelineInfo;
        }
    }
}
