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

import com.alibaba.nacos.api.ai.model.agent.base.AbstractAgentEndpointRegistrationRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Complete RAD Endpoint registration batch with an effective namespace.
 *
 * @author Nacos
 * @since 3.3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentEndpointRegistrationBatch extends AbstractAgentEndpointRegistrationRequest {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
}
