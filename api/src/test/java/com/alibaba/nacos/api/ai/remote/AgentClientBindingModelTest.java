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

package com.alibaba.nacos.api.ai.remote;

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentClientRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSearchRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointOperationResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSearchResponse;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClientBindingModelTest {
    
    @Test
    void testBindingModels() {
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        liveness.setHeartbeatIntervalMillis(5L);
        liveness.setUnhealthyTimeoutMillis(15L);
        liveness.setExpireTimeoutMillis(30L);
        assertEquals(5L, liveness.getHeartbeatIntervalMillis());
        assertEquals(15L, liveness.getUnhealthyTimeoutMillis());
        assertEquals(30L, liveness.getExpireTimeoutMillis());
        assertEquals(50404, ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode());
    }
    
    @Test
    void testGrpcRequests() throws Exception {
        AgentSearchRequest search = new AgentSearchRequest();
        search.setNamespaceId("search-ns");
        AgentSearchRpcRequest searchRequest = new AgentSearchRpcRequest();
        searchRequest.setSearchRequest(search);
        assertSame(search, searchRequest.getSearchRequest());
        assertEquals(Constants.AI.AI_MODULE, searchRequest.getModule());
        assertEquals("search-ns", searchRequest.extractNamespaceId());
        assertNull(searchRequest.extractAgentName());
        
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        discovery.setNamespaceId("discovery-ns");
        AgentReference reference = new AgentReference();
        reference.setAgentName("discovery-agent");
        discovery.setReference(reference);
        AgentDiscoveryRpcRequest discoveryRequest = new AgentDiscoveryRpcRequest();
        discoveryRequest.setDiscoveryRequest(discovery);
        assertSame(discovery, discoveryRequest.getDiscoveryRequest());
        assertEquals(Constants.AI.AI_MODULE, discoveryRequest.getModule());
        assertEquals("discovery-ns", discoveryRequest.extractNamespaceId());
        assertEquals("discovery-agent", discoveryRequest.extractAgentName());
        
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setNamespaceId("register-ns");
        batch.setAgentName("register-agent");
        AgentEndpointRegisterRpcRequest registerRequest =
            new AgentEndpointRegisterRpcRequest();
        registerRequest.setRegistrationBatch(batch);
        assertSame(batch, registerRequest.getRegistrationBatch());
        assertEquals(Constants.AI.AI_MODULE, registerRequest.getModule());
        assertEquals("register-ns", registerRequest.extractNamespaceId());
        assertEquals("register-agent", registerRequest.extractAgentName());
        
        AgentEndpointDeregisterRpcRequest deregisterRequest =
            new AgentEndpointDeregisterRpcRequest();
        deregisterRequest.setNamespaceId("deregister-ns");
        deregisterRequest.setAgentName("deregister-agent");
        deregisterRequest.setProtocol("a2a");
        assertEquals(Constants.AI.AI_MODULE, deregisterRequest.getModule());
        assertEquals("deregister-ns", deregisterRequest.extractNamespaceId());
        assertEquals("deregister-agent", deregisterRequest.extractAgentName());
        assertEquals("deregister-ns", deregisterRequest.getNamespaceId());
        assertEquals("deregister-agent", deregisterRequest.getAgentName());
        assertEquals("a2a", deregisterRequest.getProtocol());
        
        assertTrue(searchRequest instanceof AbstractAgentClientRpcRequest);
        String serialized = new ObjectMapper().writeValueAsString(searchRequest);
        assertTrue(serialized.contains("\"searchRequest\""));
        assertFalse(serialized.contains("extractNamespaceId"));
        assertFalse(serialized.contains("extractAgentName"));
    }
    
    @Test
    void testGrpcResponses() {
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentSearchResponse searchResponse = new AgentSearchResponse();
        searchResponse.setPage(page);
        assertSame(page, searchResponse.getPage());
        
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        AgentDiscoveryResponse discoveryResponse = new AgentDiscoveryResponse();
        discoveryResponse.setDiscoveryResult(result);
        assertSame(result, discoveryResponse.getDiscoveryResult());
        
        assertNotNull(new AgentEndpointOperationResponse());
    }
}
