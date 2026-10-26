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

import java.io.Serializable;
import java.util.Map;
import java.util.List;

/**
 * Shared endpoint value used by declared and runtime Agent views.
 *
 * @author Nacos
 */
public class Endpoint implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String uri;
    
    private String transport;
    
    /** Lower values have higher priority; defaults to the minimum priority value, zero. */
    private Integer priority = 0;
    
    /** Relative weight among endpoints at the same priority; defaults to one. */
    private Double weight = 1D;
    
    private Map<String, String> metadata;
    
    /** Registration health defaults to true; runtime reads return the current health. */
    private Boolean healthy = true;
    
    /** One optional registration binding; query results may aggregate several publishers. */
    private List<RuntimeVersionBinding> bindings;
    
    /** Registration enablement defaults to true; Naming operational overrides take precedence. */
    private Boolean enabled = true;
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public String getTransport() {
        return transport;
    }
    
    public void setTransport(String transport) {
        this.transport = transport;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public Double getWeight() {
        return weight;
    }
    
    public void setWeight(Double weight) {
        this.weight = weight;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public Boolean getHealthy() {
        return healthy;
    }
    
    public void setHealthy(Boolean healthy) {
        this.healthy = healthy;
    }
    
    public List<RuntimeVersionBinding> getBindings() {
        return bindings;
    }
    
    public void setBindings(List<RuntimeVersionBinding> bindings) {
        this.bindings = bindings;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
}
