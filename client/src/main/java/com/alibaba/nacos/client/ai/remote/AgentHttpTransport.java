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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * HTTP implementation of the protocol-neutral Agent transport.
 *
 * @author Nacos
 */
public class AgentHttpTransport implements AgentTransport {
    
    private final AiHttpClientProxy clientProxy;
    
    public AgentHttpTransport(AiHttpClientProxy clientProxy) {
        this.clientProxy = clientProxy;
    }
    
    @Override
    public AgentTransportType getType() {
        return AgentTransportType.HTTP;
    }
    
    @Override
    public AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException {
        return clientProxy.publishAgent(request);
    }
    
    @Override
    public Page<AgentCatalogEntry> searchAgents(AgentSearchRequest request)
        throws NacosException {
        return clientProxy.searchAgents(request);
    }
    
    @Override
    public AgentDiscoveryResult discoverAgent(AgentDiscoveryRequest request)
        throws NacosException {
        return clientProxy.discoverAgent(request);
    }
    
    @Override
    public ClientLivenessInfo registerAgentEndpoints(AgentEndpointRegistrationBatch batch)
        throws NacosException {
        return clientProxy.registerAgentEndpoints(batch);
    }
    
    @Override
    public void deregisterAgentEndpoints(String namespaceId, String agentName, String protocol)
        throws NacosException {
        clientProxy.deregisterAgentEndpoints(namespaceId, agentName, protocol);
    }
    
    @Override
    public ClientLivenessInfo heartbeatAgentEndpoints() throws NacosException {
        return clientProxy.heartbeatAgentEndpoints();
    }
}
