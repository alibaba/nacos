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
import java.util.List;

/**
 * One endpoint in a runtime management snapshot.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuntimeEndpointSnapshotItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Endpoint endpoint;
    
    private List<RuntimeVersionBinding> bindings;
    
    private RuntimeEndpointState state;
    
    private Boolean enabled;
    
    private Boolean healthy;
    
    private Long lastUpdatedTime;
    
    public Endpoint getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }
    
    public List<RuntimeVersionBinding> getBindings() {
        return bindings;
    }
    
    public void setBindings(List<RuntimeVersionBinding> bindings) {
        this.bindings = bindings;
    }
    
    public RuntimeEndpointState getState() {
        return state;
    }
    
    public void setState(RuntimeEndpointState state) {
        this.state = state;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public Boolean getHealthy() {
        return healthy;
    }
    
    public void setHealthy(Boolean healthy) {
        this.healthy = healthy;
    }
    
    public Long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
    
    public void setLastUpdatedTime(Long lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
    }
}
