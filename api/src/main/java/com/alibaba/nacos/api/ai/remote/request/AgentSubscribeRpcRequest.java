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

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;

/**
 * Install one connection-owned RAD Watch intent.
 *
 * @author Nacos
 */
public class AgentSubscribeRpcRequest extends AbstractAgentClientRpcRequest {
    
    private String clientWatchId;
    
    private AgentDiscoveryRequest discoveryRequest;
    
    private String materializedFingerprint;
    
    @Override
    public String extractNamespaceId() {
        return discoveryRequest == null ? null : discoveryRequest.getNamespaceId();
    }
    
    @Override
    public String extractAgentName() {
        AgentReference reference =
            discoveryRequest == null ? null : discoveryRequest.getReference();
        return reference == null ? null : reference.getAgentName();
    }
    
    public String getClientWatchId() {
        return clientWatchId;
    }
    
    public void setClientWatchId(String clientWatchId) {
        this.clientWatchId = clientWatchId;
    }
    
    public AgentDiscoveryRequest getDiscoveryRequest() {
        return discoveryRequest;
    }
    
    public void setDiscoveryRequest(AgentDiscoveryRequest discoveryRequest) {
        this.discoveryRequest = discoveryRequest;
    }
    
    public String getMaterializedFingerprint() {
        return materializedFingerprint;
    }
    
    public void setMaterializedFingerprint(String materializedFingerprint) {
        this.materializedFingerprint = materializedFingerprint;
    }
}
