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

/**
 * Fixed-version ZIP transfer for the Admin and remote Console bridge.
 * Contains no source identifiers, reference ext or storage descriptor.
 *
 * @author nacos
 */
public class HubSkillDownload {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String displayName;
    
    private String sharedVersion;
    
    private byte[] zipBytes;
    
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
    
    public byte[] getZipBytes() {
        return zipBytes;
    }
    
    public void setZipBytes(byte[] zipBytes) {
        this.zipBytes = zipBytes;
    }
}
