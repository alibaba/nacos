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

package com.alibaba.nacos.api.ai.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Runtime implementation version and the Agent version range it can serve.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuntimeVersionBinding implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String runtimeVersion;
    
    private String versionRange;
    
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
