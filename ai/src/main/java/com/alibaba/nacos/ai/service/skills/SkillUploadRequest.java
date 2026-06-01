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

package com.alibaba.nacos.ai.service.skills;

/**
 * Request for uploading a skill package.
 *
 * @author nacos
 */
public class SkillUploadRequest {
    
    private String namespaceId;
    
    private byte[] zipBytes;
    
    private boolean overwrite;
    
    private String targetVersion;
    
    private String commitMsg;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public byte[] getZipBytes() {
        return zipBytes;
    }
    
    public boolean isOverwrite() {
        return overwrite;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public String getCommitMsg() {
        return commitMsg;
    }
    
    /**
     * Builder for {@link SkillUploadRequest}.
     */
    public static class Builder {
        
        private final SkillUploadRequest request = new SkillUploadRequest();
        
        public Builder namespaceId(String namespaceId) {
            request.namespaceId = namespaceId;
            return this;
        }
        
        public Builder zipBytes(byte[] zipBytes) {
            request.zipBytes = zipBytes;
            return this;
        }
        
        public Builder overwrite(boolean overwrite) {
            request.overwrite = overwrite;
            return this;
        }
        
        public Builder targetVersion(String targetVersion) {
            request.targetVersion = targetVersion;
            return this;
        }
        
        public Builder commitMsg(String commitMsg) {
            request.commitMsg = commitMsg;
            return this;
        }
        
        public SkillUploadRequest build() {
            return request;
        }
    }
}
