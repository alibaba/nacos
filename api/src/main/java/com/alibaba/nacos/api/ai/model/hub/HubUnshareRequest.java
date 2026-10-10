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
 * Request to disable an existing Hub share without deleting the source.
 *
 * @author nacos
 */
public class HubUnshareRequest {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long expectedMetaVersion;
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
    
    public Long getExpectedMetaVersion() {
        return expectedMetaVersion;
    }
    
    public void setExpectedMetaVersion(Long expectedMetaVersion) {
        this.expectedMetaVersion = expectedMetaVersion;
    }
    
}
