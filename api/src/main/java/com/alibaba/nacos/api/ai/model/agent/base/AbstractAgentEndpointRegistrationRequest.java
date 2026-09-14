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

package com.alibaba.nacos.api.ai.model.agent.base;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Shared complete Endpoint publication fields without namespace.
 *
 * @author Nacos
 * @since 3.3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractAgentEndpointRegistrationRequest
    extends AbstractAgentEndpointRequest {
    
    private static final long serialVersionUID = 1L;
    
    private String runtimeVersion;
    
    private String versionRange;
    
    /**
     * Initialize fields shared by concrete Agent models.
     */
    protected AbstractAgentEndpointRegistrationRequest() {
    }
    
    public String getRuntimeVersion() {
        return runtimeVersion;
    }
    
    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }
    
    public String getVersionRange() {
        return versionRange;
    }
    
    public void setVersionRange(String versionRange) {
        this.versionRange = versionRange;
    }
}
