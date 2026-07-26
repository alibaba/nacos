/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;

/**
 * Register or Deregister endpoint for agent to nacos AI module request.
 *
 * @author xiweng.yy
 */
public class AgentEndpointRequest extends AbstractAgentRequest {
    
    private AgentEndpoint endpoint;
    
    /**
     * Should be {@link AiRemoteConstants#REGISTER_ENDPOINT} or {@link AiRemoteConstants#DE_REGISTER_ENDPOINT}.
     */
    private String type;
    
    public AgentEndpoint getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(AgentEndpoint endpoint) {
        this.endpoint = endpoint;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
}
