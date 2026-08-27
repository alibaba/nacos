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
import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryNotifyResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentUnsubscribeRpcResponse;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWatchRpcBindingTest extends BasicRequestTest {
    
    private static final String FINGERPRINT =
        "sha256-canonical-json-v1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    
    @Test
    void testSubscribeRequestAndResponseRoundTrip() throws Exception {
        AgentSubscribeRpcRequest request = new AgentSubscribeRpcRequest();
        request.setClientWatchId("watch:client-1");
        request.setDiscoveryRequest(discoveryRequest());
        request.setMaterializedFingerprint(FINGERPRINT);
        
        AgentSubscribeRpcRequest restored = mapper.readValue(mapper.writeValueAsString(request),
            AgentSubscribeRpcRequest.class);
        assertEquals(Constants.AI.AI_MODULE, restored.getModule());
        assertEquals("public", restored.extractNamespaceId());
        assertEquals("demo-agent", restored.extractAgentName());
        assertEquals("watch:client-1", restored.getClientWatchId());
        assertEquals(FINGERPRINT, restored.getMaterializedFingerprint());
        
        AgentSubscribeRpcResponse response = new AgentSubscribeRpcResponse();
        response.setWatchKey("server-watch");
        response.setObservedFingerprint(FINGERPRINT);
        response.setRefreshRequired(true);
        AgentSubscribeRpcResponse restoredResponse = mapper.readValue(
            mapper.writeValueAsString(response), AgentSubscribeRpcResponse.class);
        assertEquals("server-watch", restoredResponse.getWatchKey());
        assertEquals(FINGERPRINT, restoredResponse.getObservedFingerprint());
        assertTrue(restoredResponse.isRefreshRequired());
    }
    
    @Test
    void testUnsubscribeBindingIsOpaqueAndIdempotentResponseHasNoPayload() throws Exception {
        AgentUnsubscribeRpcRequest request = new AgentUnsubscribeRpcRequest();
        request.setWatchKey("server-watch");
        AgentUnsubscribeRpcRequest restored = mapper.readValue(mapper.writeValueAsString(request),
            AgentUnsubscribeRpcRequest.class);
        assertEquals("server-watch", restored.getWatchKey());
        assertNull(restored.extractNamespaceId());
        assertNull(restored.extractAgentName());
        assertEquals(Constants.AI.AI_MODULE, restored.getModule());
        assertFalse(mapper.writeValueAsString(new AgentUnsubscribeRpcResponse())
            .contains("watchKey"));
    }
    
    @Test
    void testNotifyCarriesOnlyHintAndAcknowledgement() throws Exception {
        AgentDiscoveryNotifyRequest request = new AgentDiscoveryNotifyRequest();
        request.setWatchKey("server-watch");
        request.setEventType(AgentWatchEventType.INVALIDATE);
        request.setObservedFingerprint(FINGERPRINT);
        String json = mapper.writeValueAsString(request);
        assertFalse(json.contains("discoveryResult"));
        AgentDiscoveryNotifyRequest restored = mapper.readValue(json,
            AgentDiscoveryNotifyRequest.class);
        assertEquals(Constants.AI.AI_MODULE, restored.getModule());
        assertEquals(AgentWatchEventType.INVALIDATE, restored.getEventType());
        assertEquals(FINGERPRINT, restored.getObservedFingerprint());
        assertNull(restored.getErrorCode());
        
        AgentDiscoveryNotifyResponse response = new AgentDiscoveryNotifyResponse();
        response.setWatchKey("server-watch");
        assertFalse(response.isSuccess());
        response.setAccepted(true);
        assertTrue(response.isSuccess());
        AgentDiscoveryNotifyResponse restoredResponse = mapper.readValue(
            mapper.writeValueAsString(response), AgentDiscoveryNotifyResponse.class);
        assertEquals("server-watch", restoredResponse.getWatchKey());
        assertTrue(restoredResponse.isAccepted());
        assertTrue(restoredResponse.isSuccess());
        
        restoredResponse.setErrorInfo(503, "retry");
        assertFalse(restoredResponse.isSuccess());
    }
    
    @Test
    void testNotifyOtherEventAccessors() {
        AgentDiscoveryNotifyRequest request = new AgentDiscoveryNotifyRequest();
        request.setEventType(AgentWatchEventType.TERMINATED);
        request.setErrorCode(404);
        assertEquals(AgentWatchEventType.TERMINATED, request.getEventType());
        assertEquals(404, request.getErrorCode());
    }
    
    private AgentDiscoveryRequest discoveryRequest() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("demo-agent");
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId("public");
        result.setReference(reference);
        return result;
    }
}
