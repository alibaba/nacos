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

import java.util.Map;

/**
 * Public content of exactly one pinned, still-authorized shared Skill version.
 *
 * <p>S2 content reads must load the actual source version, not the Hub version
 * descriptor. No source Namespace ID, source database ID, storage or raw ext
 * is exposed in the content response. Any returned resources must be sanitized
 * by the Hub read service before populating this DTO.</p>
 *
 * @author nacos
 */
public class HubSkillContent {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String displayName;
    
    private String sharedVersion;
    
    private String skillMd;
    
    private Map<String, HubSkillResource> resource;
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getSharedVersion() {
        return sharedVersion;
    }
    
    public void setSharedVersion(String sharedVersion) {
        this.sharedVersion = sharedVersion;
    }
    
    public String getSkillMd() {
        return skillMd;
    }
    
    public void setSkillMd(String skillMd) {
        this.skillMd = skillMd;
    }
    
    public Map<String, HubSkillResource> getResource() {
        return resource;
    }
    
    public void setResource(Map<String, HubSkillResource> resource) {
        this.resource = resource;
    }
}
