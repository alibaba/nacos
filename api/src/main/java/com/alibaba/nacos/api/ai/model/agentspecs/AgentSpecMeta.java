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

package com.alibaba.nacos.api.ai.model.agentspecs;

import java.util.List;

/**
 * AgentSpec metadata for admin API response.
 * Contains governance metadata and all version summaries.
 *
 * @author nacos
 */
public class AgentSpecMeta extends AgentSpecSummary {
    
    private List<AgentSpecVersionSummary> versions;
    
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
        
        public Long getDownloadCount() {
            return downloadCount;
        }
        
        public void setDownloadCount(Long downloadCount) {
            this.downloadCount = downloadCount;
        }
    }
}
