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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentHttpTransportTest {
    
    @Mock
    private AiHttpClientProxy clientProxy;
    
    @Test
    void agentOperationsDelegateToHttpClientProxy() throws NacosException {
        AgentHttpTransport transport = new AgentHttpTransport(clientProxy);
        AgentPublishRequest publishRequest = new AgentPublishRequest();
        AgentVersionDetail version = new AgentVersionDetail();
        AgentSearchRequest searchRequest = new AgentSearchRequest();
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(clientProxy.publishAgent(publishRequest)).thenReturn(version);
        when(clientProxy.searchAgents(searchRequest)).thenReturn(page);
        when(clientProxy.discoverAgent(discoveryRequest)).thenReturn(discovery);
        when(clientProxy.registerAgentEndpoints(batch)).thenReturn(liveness);
        when(clientProxy.heartbeatAgentEndpoints()).thenReturn(liveness);
        
        assertEquals(AgentTransportType.HTTP, transport.getType());
        assertSame(version, transport.publishAgent(publishRequest));
        assertSame(page, transport.searchAgents(searchRequest));
        assertSame(discovery, transport.discoverAgent(discoveryRequest));
        assertSame(liveness, transport.registerAgentEndpoints(batch));
        transport.deregisterAgentEndpoints("public", "agent", "a2a");
        assertSame(liveness, transport.heartbeatAgentEndpoints());
        verify(clientProxy).deregisterAgentEndpoints("public", "agent", "a2a");
    }
}
