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

package com.alibaba.nacos.ai.model.agentspecs;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;

import java.util.List;
import java.util.Map;

/**
 * AgentSpec detail for admin API response. Contains full agentspec content plus version governance info.
 *
 * @author nacos
 * @since 3.2.0
 */
public class AgentSpecAdminDetail {

    /**
     * Full agentspec content (manifest, resources, etc.).
     */
    private AgentSpec agentSpec;

    /**
     * Whether the agentspec is enabled globally.
     */
    private boolean enable;

    /**
     * Current resolved version (latest label or editing version).
     */
    private String version;

    /**
     * Version status: draft / reviewing / online / offline.
     */
    private String versionStatus;

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
     * All version summaries for this agentspec.
     */
    private List<AgentSpecVersionSummary> versions;

    public AgentSpec getAgentSpec() {
        return agentSpec;
    }

    public void setAgentSpec(AgentSpec agentSpec) {
        this.agentSpec = agentSpec;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionStatus() {
        return versionStatus;
    }

    public void setVersionStatus(String versionStatus) {
        this.versionStatus = versionStatus;
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

    public List<AgentSpecVersionSummary> getVersions() {
        return versions;
    }

    public void setVersions(List<AgentSpecVersionSummary> versions) {
        this.versions = versions;
    }

    /**
     * Summary of a single agentspec version for admin display.
     */
    public static class AgentSpecVersionSummary {

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
