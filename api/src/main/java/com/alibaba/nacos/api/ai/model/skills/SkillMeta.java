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

import java.util.List;

/**
 * Skill metadata for admin API response.
 * Contains governance metadata and all version summaries.
 *
 * @author nacos
 */
public class SkillMeta extends SkillSummary {
    
    /**
     * All version summaries for this skill.
     */
    private List<SkillVersionSummary> versions;
    
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
        
        private String commitMsg;
        
        private Long createTime;
        
        private Long updateTime;
        
        private String publishPipelineInfo;
        
        /**
         * Download count for this version.
         */
        private Long downloadCount;
        
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
        
        public String getCommitMsg() {
            return commitMsg;
        }
        
        public void setCommitMsg(String commitMsg) {
            this.commitMsg = commitMsg;
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
        
        public Long getDownloadCount() {
            return downloadCount;
        }
        
        public void setDownloadCount(Long downloadCount) {
            this.downloadCount = downloadCount;
        }
    }
}
