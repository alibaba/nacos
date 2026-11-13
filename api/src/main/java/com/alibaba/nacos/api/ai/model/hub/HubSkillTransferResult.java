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

package com.alibaba.nacos.api.ai.model.hub;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Response deliberately omits source namespace IDs and storage descriptors. */
public class HubSkillTransferResult {
    
    private String targetNamespaceId;
    private String targetName;
    private String draftVersion;
    private String status;
    private String origin;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long targetResourceId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    public String getTargetNamespaceId() {
        return targetNamespaceId;
    }
    
    public void setTargetNamespaceId(String targetNamespaceId) {
        this.targetNamespaceId = targetNamespaceId;
    }
    
    public String getTargetName() {
        return targetName;
    }
    
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }
    
    public String getDraftVersion() {
        return draftVersion;
    }
    
    public void setDraftVersion(String draftVersion) {
        this.draftVersion = draftVersion;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getOrigin() {
        return origin;
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    public Long getTargetResourceId() {
        return targetResourceId;
    }
    
    public void setTargetResourceId(Long targetResourceId) {
        this.targetResourceId = targetResourceId;
    }
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
}
