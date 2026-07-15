/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

/**
 * Request for checking a skill upload before the full ZIP is submitted.
 *
 * @author nacos
 */
public class SkillUploadPrecheckRequest {
    
    private String namespaceId;
    
    private String skillName;
    
    private String description;
    
    private String parsedVersion;
    
    private String versionSource;
    
    private String targetVersion;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getSkillName() {
        return skillName;
    }
    
    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getParsedVersion() {
        return parsedVersion;
    }
    
    public void setParsedVersion(String parsedVersion) {
        this.parsedVersion = parsedVersion;
    }
    
    public String getVersionSource() {
        return versionSource;
    }
    
    public void setVersionSource(String versionSource) {
        this.versionSource = versionSource;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }
}
