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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSearchRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentPublishRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentUnsubscribeRpcRequest;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentClientParamExtractorTest {
    
    @Test
    void testHttpExtractor() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("namespaceId")).thenReturn("team");
        when(request.getParameter("agentName")).thenReturn("demo");
        
        List<ParamInfo> actual = new AgentClientHttpParamExtractor().extractParam(request);
        
        assertEquals(1, actual.size());
        assertEquals("team", actual.get(0).getNamespaceId());
        assertEquals("demo", actual.get(0).getAgentName());
    }
    
    @Test
    void testHttpWatchExtractorUsesNestedNamespaceOnly() throws Exception {
        AgentReference reference = new AgentReference();
        reference.setAgentName("watch-agent");
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        discovery.setNamespaceId(null);
        discovery.setReference(reference);
        AgentWatchBatchItem item = new AgentWatchBatchItem();
        item.setClientWatchId("watch-1");
        item.setDiscoveryRequest(discovery);
        item.setMaterializedFingerprint(AgentDiscoveryCanonicalizer.ALGORITHM_ID + ":"
            + "a".repeat(64));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/nacos/v3/client/ai/agents/watch");
        when(request.getParameter("watches"))
            .thenReturn(JsonUtils.toJson(Collections.singletonList(item)));
        
        ParamInfo actual = new AgentClientHttpParamExtractor().extractParam(request).get(0);
        assertEquals("public", actual.getNamespaceId());
        assertNull(actual.getAgentName());
    }
    
    @Test
    void testHttpWatchExtractorDefersMalformedPayloadValidation() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/nacos/v3/client/ai/agents/watch");
        when(request.getParameter("watches")).thenReturn("[]");
        
        ParamInfo actual = new AgentClientHttpParamExtractor().extractParam(request).get(0);
        
        assertNull(actual.getNamespaceId());
        assertNull(actual.getAgentName());
    }
    
    @Test
    void testRpcSearchExtraction() throws Exception {
        AgentSearchRpcRequest request = new AgentSearchRpcRequest();
        assertEmpty(extract(request));
        
        AgentSearchRequest search = new AgentSearchRequest();
        search.setNamespaceId("search-ns");
        request.setSearchRequest(search);
        ParamInfo actual = extract(request);
        assertEquals("search-ns", actual.getNamespaceId());
        assertNull(actual.getAgentName());
    }
    
    @Test
    void testRpcDiscoveryExtraction() throws Exception {
        AgentDiscoveryRpcRequest request = new AgentDiscoveryRpcRequest();
        assertEmpty(extract(request));
        
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        discovery.setNamespaceId("discovery-ns");
        request.setDiscoveryRequest(discovery);
        ParamInfo withoutReference = extract(request);
        assertEquals("discovery-ns", withoutReference.getNamespaceId());
        assertNull(withoutReference.getAgentName());
        
        AgentReference reference = new AgentReference();
        reference.setAgentName("demo");
        discovery.setReference(reference);
        assertEquals("demo", extract(request).getAgentName());
    }
    
    @Test
    void testRpcRegistrationExtraction() throws Exception {
        AgentEndpointRegisterRpcRequest request = new AgentEndpointRegisterRpcRequest();
        assertEmpty(extract(request));
        
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setNamespaceId("register-ns");
        batch.setAgentName("demo");
        request.setRegistrationBatch(batch);
        ParamInfo actual = extract(request);
        assertEquals("register-ns", actual.getNamespaceId());
        assertEquals("demo", actual.getAgentName());
    }
    
    @Test
    void testRpcPublishExtraction() throws Exception {
        AgentPublishRpcRequest request = new AgentPublishRpcRequest();
        request.setNamespaceId("publish-ns");
        assertEquals("publish-ns", extract(request).getNamespaceId());
        
        AgentPublishRequest publication = new AgentPublishRequest();
        publication.setAgentName("demo");
        request.setPublishRequest(publication);
        ParamInfo actual = extract(request);
        assertEquals("publish-ns", actual.getNamespaceId());
        assertEquals("demo", actual.getAgentName());
    }
    
    @Test
    void testRpcDeregistrationAndOtherExtraction() throws Exception {
        AgentEndpointDeregisterRpcRequest request = new AgentEndpointDeregisterRpcRequest();
        assertEmpty(extract(request));
        
        request.setNamespaceId("deregister-ns");
        request.setAgentName("demo");
        ParamInfo actual = extract(request);
        assertEquals("deregister-ns", actual.getNamespaceId());
        assertEquals("demo", actual.getAgentName());
        
        assertEmpty(extract(new NotifySubscriberRequest()));
    }
    
    @Test
    void testRpcWatchExtraction() throws Exception {
        AgentSubscribeRpcRequest subscribe = new AgentSubscribeRpcRequest();
        assertEmpty(extract(subscribe));
        
        AgentReference reference = new AgentReference();
        reference.setAgentName("watch-agent");
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        discovery.setNamespaceId("watch-ns");
        discovery.setReference(reference);
        subscribe.setDiscoveryRequest(discovery);
        ParamInfo actual = extract(subscribe);
        assertEquals("watch-ns", actual.getNamespaceId());
        assertEquals("watch-agent", actual.getAgentName());
        
        assertEmpty(extract(new AgentUnsubscribeRpcRequest()));
    }
    
    private ParamInfo extract(Request request) throws Exception {
        List<ParamInfo> result = new AgentClientRpcParamExtractor().extractParam(request);
        assertEquals(1, result.size());
        return result.get(0);
    }
    
    private void assertEmpty(ParamInfo actual) {
        assertNull(actual.getNamespaceId());
        assertNull(actual.getAgentName());
    }
}
