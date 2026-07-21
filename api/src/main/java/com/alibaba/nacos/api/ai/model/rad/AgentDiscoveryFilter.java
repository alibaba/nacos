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

import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Optional filter that narrows one Agent discovery result.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentDiscoveryFilter implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private List<String> protocols;
    
    private String protocolVersion;
    
    private List<String> transports;
    
    private List<EndpointSource> endpointSources;
    
    private Map<String, String> metadataSelector;
    
    public List<String> getProtocols() {
        return protocols;
    }
    
    public void setProtocols(List<String> protocols) {
        this.protocols = protocols;
    }
    
    public String getProtocolVersion() {
        return protocolVersion;
    }
    
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
    
    public List<String> getTransports() {
        return transports;
    }
    
    public void setTransports(List<String> transports) {
        this.transports = transports;
    }
    
    public List<EndpointSource> getEndpointSources() {
        return endpointSources;
    }
    
    public void setEndpointSources(List<EndpointSource> endpointSources) {
        this.endpointSources = endpointSources;
    }
    
    public Map<String, String> getMetadataSelector() {
        return metadataSelector;
    }
    
    public void setMetadataSelector(Map<String, String> metadataSelector) {
        this.metadataSelector = metadataSelector;
    }
}
