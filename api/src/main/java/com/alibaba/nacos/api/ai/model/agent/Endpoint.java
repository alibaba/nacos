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
    private int priority;
    
    /** Relative weight among endpoints at the same priority; defaults to one. */
    private double weight = 1D;
    
    private Map<String, String> metadata;
    
    /** Registration health defaults to true; runtime reads return the current health. */
    private boolean healthy = true;
    
    /** Nacos-maintained fields. Ignored when submitted in a write request. */
    private List<RuntimeVersionBinding> bindings;
    
    /** Whether the endpoint is enabled; maintained by Nacos and defaults to true. */
    private boolean enabled = true;
    
    private RuntimeEndpointState state;
    
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
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public double getWeight() {
        return weight;
    }
    
    public void setWeight(double weight) {
        this.weight = weight;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public boolean getHealthy() {
        return healthy;
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    
    public List<RuntimeVersionBinding> getBindings() {
        return bindings;
    }
    
    public void setBindings(List<RuntimeVersionBinding> bindings) {
        this.bindings = bindings;
    }
    
    public boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public RuntimeEndpointState getState() {
        return state;
    }
    
    public void setState(RuntimeEndpointState state) {
        this.state = state;
    }
}
