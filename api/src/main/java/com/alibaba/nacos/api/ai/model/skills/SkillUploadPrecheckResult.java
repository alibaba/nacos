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

import java.util.ArrayList;
import java.util.List;

/**
 * Result of checking a skill ZIP upload before applying it.
 *
 * @author nacos
 */
public class SkillUploadPrecheckResult {
    
    public static final String STATUS_VALID = "VALID";
    
    public static final String STATUS_WARNING = "WARNING";
    
    public static final String STATUS_CONFLICT = "CONFLICT";
    
    public static final String STATUS_FORBIDDEN = "FORBIDDEN";
    
    public static final String ACTION_CREATE_DRAFT = "CREATE_DRAFT";
    
    public static final String ACTION_OVERWRITE_DRAFT = "OVERWRITE_DRAFT";
    
    public static final String ACTION_DELETE_DRAFT_AND_CREATE = "DELETE_DRAFT_AND_CREATE";
    
    public static final String CONFLICT_EXISTING_SKILL = "existing_skill";
    
    public static final String CONFLICT_VERSION_EXISTS = "version_exists";
    
    public static final String CONFLICT_DRAFT_EXISTS = "draft_exists";
    
    public static final String CONFLICT_REVIEWING_VERSION = "reviewing_version";
    
    public static final String CONFLICT_NO_PERMISSION = "no_permission";
    
    private String namespaceId;
    
    private String skillName;
    
    private String description;
    
    private String parsedVersion;
    
    private String resolvedVersion;
    
    private String versionSource;
    
    private boolean exists;
    
    private boolean writable;
    
    private boolean versionExists;
    
    private boolean draftExists;
    
    private boolean reviewingExists;
    
    private String editingVersion;
    
    private String reviewingVersion;
    
    private String status;
    
    private List<String> conflictTypes = new ArrayList<>();
    
    private List<String> warnings = new ArrayList<>();
    
    private List<String> errors = new ArrayList<>();
    
    private List<Action> actions = new ArrayList<>();
    
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
    
    public String getResolvedVersion() {
        return resolvedVersion;
    }
    
    public void setResolvedVersion(String resolvedVersion) {
        this.resolvedVersion = resolvedVersion;
    }
    
    public String getVersionSource() {
        return versionSource;
    }
    
    public void setVersionSource(String versionSource) {
        this.versionSource = versionSource;
    }
    
    public boolean isExists() {
        return exists;
    }
    
    public void setExists(boolean exists) {
        this.exists = exists;
    }
    
    public boolean isWritable() {
        return writable;
    }
    
    public void setWritable(boolean writable) {
        this.writable = writable;
    }
    
    public boolean isVersionExists() {
        return versionExists;
    }
    
    public void setVersionExists(boolean versionExists) {
        this.versionExists = versionExists;
    }
    
    public boolean isDraftExists() {
        return draftExists;
    }
    
    public void setDraftExists(boolean draftExists) {
        this.draftExists = draftExists;
    }
    
    public boolean isReviewingExists() {
        return reviewingExists;
    }
    
    public void setReviewingExists(boolean reviewingExists) {
        this.reviewingExists = reviewingExists;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public List<String> getConflictTypes() {
        return conflictTypes;
    }
    
    public void setConflictTypes(List<String> conflictTypes) {
        this.conflictTypes = conflictTypes;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public List<Action> getActions() {
        return actions;
    }
    
    public void setActions(List<Action> actions) {
        this.actions = actions;
    }
    
    public void addConflictType(String conflictType) {
        this.conflictTypes.add(conflictType);
    }
    
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    public void addError(String error) {
        this.errors.add(error);
    }
    
    public void addAction(String type, String resultVersion, String description) {
        this.actions.add(new Action(type, resultVersion, description));
    }
    
    /**
     * Upload action that can be selected after precheck.
     */
    public static class Action {
        
        private String type;
        
        private String resultVersion;
        
        private String description;
        
        public Action() {
        }
        
        public Action(String type, String resultVersion, String description) {
            this.type = type;
            this.resultVersion = resultVersion;
            this.description = description;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getResultVersion() {
            return resultVersion;
        }
        
        public void setResultVersion(String resultVersion) {
            this.resultVersion = resultVersion;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
}
