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

/**
 * Result of checking a skill ZIP upload before applying it.
 *
 * @author nacos
 */
public class SkillUploadPrecheckResult {
    
    public static final String PRECHECK_CODE_READY = "READY";
    
    public static final String PRECHECK_CODE_VERSION_ADJUSTED = "VERSION_ADJUSTED";
    
    public static final String PRECHECK_CODE_DRAFT_EXISTS = "DRAFT_EXISTS";
    
    public static final String PRECHECK_CODE_REVIEWING_EXISTS = "REVIEWING_EXISTS";
    
    public static final String PRECHECK_CODE_NO_PERMISSION = "NO_PERMISSION";
    
    public static final String PRECHECK_CODE_NOT_A_SKILL = "NOT_A_SKILL";
    
    public static final String PRECHECK_CODE_INVALID_SKILL = "INVALID_SKILL";
    
    public static final String ACTION_CREATE_DRAFT = "CREATE_DRAFT";
    
    public static final String ACTION_OVERWRITE_DRAFT = "OVERWRITE_DRAFT";
    
    public static final String ACTION_DELETE_DRAFT_AND_CREATE = "DELETE_DRAFT_AND_CREATE";
    
    private String namespaceId;
    
    private String entryPath;
    
    private String skillName;
    
    private String reason;
    
    private String owner;
    
    private String maxPublishedVersion;
    
    private String parsedVersion;
    
    private String targetVersion;
    
    private boolean exists;
    
    private String editingVersion;
    
    private String reviewingVersion;
    
    private String precheckCode;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getEntryPath() {
        return entryPath;
    }
    
    public void setEntryPath(String entryPath) {
        this.entryPath = entryPath;
    }
    
    public String getSkillName() {
        return skillName;
    }
    
    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getMaxPublishedVersion() {
        return maxPublishedVersion;
    }
    
    public void setMaxPublishedVersion(String maxPublishedVersion) {
        this.maxPublishedVersion = maxPublishedVersion;
    }
    
    public String getParsedVersion() {
        return parsedVersion;
    }
    
    public void setParsedVersion(String parsedVersion) {
        this.parsedVersion = parsedVersion;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }
    
    public boolean isExists() {
        return exists;
    }
    
    public void setExists(boolean exists) {
        this.exists = exists;
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
    
    public String getPrecheckCode() {
        return precheckCode;
    }
    
    public void setPrecheckCode(String precheckCode) {
        this.precheckCode = precheckCode;
    }
}
