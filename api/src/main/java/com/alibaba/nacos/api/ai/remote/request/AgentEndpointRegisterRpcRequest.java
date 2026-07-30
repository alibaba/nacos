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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;

/**
 * gRPC binding for a complete RAD Agent Endpoint registration batch.
 *
 * @author Nacos
 */
public class AgentEndpointRegisterRpcRequest extends AbstractAgentClientRpcRequest {
    
    private AgentEndpointRegistrationBatch registrationBatch;
    
    @Override
    public String extractNamespaceId() {
        return registrationBatch == null ? null : registrationBatch.getNamespaceId();
    }
    
    @Override
    public String extractAgentName() {
        return registrationBatch == null ? null : registrationBatch.getAgentName();
    }
    
    public AgentEndpointRegistrationBatch getRegistrationBatch() {
        return registrationBatch;
    }
    
    public void setRegistrationBatch(AgentEndpointRegistrationBatch registrationBatch) {
        this.registrationBatch = registrationBatch;
    }
}
