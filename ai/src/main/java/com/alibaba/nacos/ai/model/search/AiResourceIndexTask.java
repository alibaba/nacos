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

package com.alibaba.nacos.ai.model.search;

/**
 * Durable resource-level AI resource index maintenance task.
 *
 * @author nacos
 */
public class AiResourceIndexTask {
    
    public static final String STAGE_BASE_INDEX = "base_index";
    
    public static final String STAGE_LLM_ENHANCEMENT = "llm_enhancement";
    
    private String taskKey;
    
    private String namespaceId;
    
    private String resourceType;
    
    private String resourceName;
    
    private String taskStage;
    
    private String status;
    
    private boolean enhancementRequested;
    
    private String enhancementFingerprint;
    
    private int attemptCount;
    
    private long revision;
    
    public String getTaskKey() {
        return taskKey;
    }
    
    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getTaskStage() {
        return taskStage;
    }
    
    public void setTaskStage(String taskStage) {
        this.taskStage = taskStage;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public boolean isEnhancementRequested() {
        return enhancementRequested;
    }
    
    public void setEnhancementRequested(boolean enhancementRequested) {
        this.enhancementRequested = enhancementRequested;
    }
    
    public String getEnhancementFingerprint() {
        return enhancementFingerprint;
    }
    
    public void setEnhancementFingerprint(String enhancementFingerprint) {
        this.enhancementFingerprint = enhancementFingerprint;
    }
    
    public int getAttemptCount() {
        return attemptCount;
    }
    
    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
    
    public long getRevision() {
        return revision;
    }
    
    public void setRevision(long revision) {
        this.revision = revision;
    }
}
