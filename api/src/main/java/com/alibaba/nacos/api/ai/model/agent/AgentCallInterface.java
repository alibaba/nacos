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
 * Protocol binding and native descriptor for an Agent version.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCallInterface implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String protocol;
    
    private String protocolVersion;
    
    private String descriptorMediaType;
    
    private Object nativeDescriptor;
    
    private List<EndpointSource> endpointSourceOrder;
    
    private List<Endpoint> declaredEndpoints;
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getProtocolVersion() {
        return protocolVersion;
    }
    
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
    
    public String getDescriptorMediaType() {
        return descriptorMediaType;
    }
    
    public void setDescriptorMediaType(String descriptorMediaType) {
        this.descriptorMediaType = descriptorMediaType;
    }
    
    public Object getNativeDescriptor() {
        return nativeDescriptor;
    }
    
    public void setNativeDescriptor(Object nativeDescriptor) {
        this.nativeDescriptor = nativeDescriptor;
    }
    
    public List<EndpointSource> getEndpointSourceOrder() {
        return endpointSourceOrder;
    }
    
    public void setEndpointSourceOrder(List<EndpointSource> endpointSourceOrder) {
        this.endpointSourceOrder = endpointSourceOrder;
    }
    
    public List<Endpoint> getDeclaredEndpoints() {
        return declaredEndpoints;
    }
    
    public void setDeclaredEndpoints(List<Endpoint> declaredEndpoints) {
        this.declaredEndpoints = declaredEndpoints;
    }
}
