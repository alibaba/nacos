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

package com.alibaba.nacos.api.ai.model.rad;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Snapshot of endpoints from one declared or runtime source.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointSet implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private EndpointSource source;
    
    private String sourceRevision;
    
    private List<Endpoint> endpoints;
    
    public EndpointSource getSource() {
        return source;
    }
    
    public void setSource(EndpointSource source) {
        this.source = source;
    }
    
    public String getSourceRevision() {
        return sourceRevision;
    }
    
    public void setSourceRevision(String sourceRevision) {
        this.sourceRevision = sourceRevision;
    }
    
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }
}
