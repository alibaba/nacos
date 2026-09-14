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

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.List;

/**
 * Shared Endpoint request fields; concrete operations retain their validation rules.
 *
 * @author Nacos
 * @since 3.3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractAgentEndpointRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String agentName;
    
    private String protocol;
    
    private List<Endpoint> endpoints;
    
    /**
     * Initialize fields shared by concrete Agent models.
     */
    protected AbstractAgentEndpointRequest() {
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }
}
