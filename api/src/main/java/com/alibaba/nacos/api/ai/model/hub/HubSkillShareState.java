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

import java.io.Serializable;

/**
 * Authorized mutation-state lookup. Do not return this DTO from public Hub directory reads.
 */
public class HubSkillShareState implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long metaVersion;
    
    private String sharedVersion;
    
    private boolean active;
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
    
    public Long getMetaVersion() {
        return metaVersion;
    }
    
    public void setMetaVersion(Long metaVersion) {
        this.metaVersion = metaVersion;
    }
    
    public String getSharedVersion() {
        return sharedVersion;
    }
    
    public void setSharedVersion(String sharedVersion) {
        this.sharedVersion = sharedVersion;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
}
