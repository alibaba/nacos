/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;

import java.util.Collection;

/**
 * Batch Register endpoints for agent to nacos AI module request.
 *
 * @author xiweng.yy
 */
public class BatchAgentEndpointRequest extends AbstractAgentRequest {
    
    private Collection<AgentEndpoint> endpoints;
    
    public Collection<AgentEndpoint> getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(Collection<AgentEndpoint> endpoints) {
        this.endpoints = endpoints;
    }
    
    /**
     * Should be {@link AiRemoteConstants#BATCH_REGISTER_ENDPOINT}.
     */
    public String getType() {
        return AiRemoteConstants.BATCH_REGISTER_ENDPOINT;
    }
    
}
