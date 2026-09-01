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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWatchLogUtilsTest {
    
    @Test
    void tokenAndFingerprintAreStableAndAbbreviated() {
        assertEquals(AgentWatchLogUtils.token("watch-id"),
            AgentWatchLogUtils.token("watch-id"));
        assertEquals(12, AgentWatchLogUtils.token("watch-id").length());
        assertEquals("-", AgentWatchLogUtils.token(null));
        assertEquals(Integer.toHexString("watch-id".hashCode()),
            AgentWatchLogUtils.token("watch-id", "unsupported"));
        assertEquals("sha256:0123456789ab", AgentWatchLogUtils.fingerprint(
            "sha256:0123456789abcdef0123456789abcdef"));
        assertEquals(AgentWatchLogUtils.token("opaque"),
            AgentWatchLogUtils.fingerprint("opaque"));
        assertEquals("-", AgentWatchLogUtils.fingerprint(null));
        String longAlgorithm = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
            + "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        String abbreviated = AgentWatchLogUtils.fingerprint(longAlgorithm + ":1234");
        assertEquals(128 + 1 + 4, abbreviated.length());
        assertTrue(abbreviated.endsWith(":1234"));
    }
    
    @Test
    void requestSummaryKeepsSelectionButHidesMetadataValuesAndLineBreaks() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("research-agent");
        reference.setLabel("stable");
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Arrays.asList("custom", "a2a"));
        filter.setProtocolVersion("1.0");
        filter.setTransports(Collections.singletonList("http"));
        filter.setEndpointSources(Collections.singletonList(EndpointSource.RUNTIME));
        Map<String, String> selector = new LinkedHashMap<String, String>();
        selector.put("zone", "secret-east");
        filter.setMetadataSelector(selector);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("tenant");
        request.setReference(reference);
        request.setFilter(filter);
        
        String summary = AgentWatchLogUtils.describeRequest(request);
        
        assertTrue(summary.contains("namespace=tenant"));
        assertTrue(summary.contains("agent=research-agent"));
        assertTrue(summary.contains("protocols=[a2a, custom]"));
        assertTrue(summary.contains("metadataSelector=[zone=#"));
        assertFalse(summary.contains("secret-east"));
        assertFalse(summary.contains("\n"));
        assertEquals("invalidRequest=IllegalArgumentException",
            AgentWatchLogUtils.describeRequest(null));
        AgentDiscoveryRequest requestWithoutFilter = new AgentDiscoveryRequest();
        requestWithoutFilter.setReference(reference);
        assertTrue(AgentWatchLogUtils.describeRequest(requestWithoutFilter)
            .contains("metadataSelector={}"));
    }
    
    @Test
    void resultSummaryContainsOnlyPublicShape() {
        AgentDiscoveryEndpoint endpoint = new AgentDiscoveryEndpoint();
        endpoint.setUri("https://agent.example.com/private-path");
        endpoint.setTransport("http");
        EndpointSet endpointSet = new EndpointSet();
        endpointSet.setSource(EndpointSource.DECLARED);
        endpointSet.setSourceRevision(
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        endpointSet.setEndpoints(Collections.singletonList(endpoint));
        AgentDiscoveryCallInterface callInterface = new AgentDiscoveryCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("1.0");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("secret", "descriptor"));
        callInterface.setEndpointSets(Collections.singletonList(endpointSet));
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("research-agent");
        result.setVersion("1.0.0");
        result.setContentDigest(
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        result.setCallInterfaces(Collections.singletonList(callInterface));
        
        String summary = AgentWatchLogUtils.describeResult(result);
        
        assertTrue(summary.contains("agent=research-agent"));
        assertTrue(summary.contains("version=1.0.0"));
        assertTrue(summary.contains("protocols=[a2a]"));
        assertTrue(summary.contains("endpointCount=1"));
        assertFalse(summary.contains("private-path"));
        assertFalse(summary.contains("descriptor"));
        assertEquals("invalidResult=IllegalArgumentException",
            AgentWatchLogUtils.describeResult(null));
        result.setCallInterfaces(Collections.<AgentDiscoveryCallInterface>emptyList());
        assertTrue(AgentWatchLogUtils.describeResult(result).contains("protocols=[]"));
    }
    
    @Test
    void tokenListDoesNotExposeOpaqueIds() {
        String result = AgentWatchLogUtils.tokens(Arrays.asList("watch-one", "watch-two"));
        assertFalse(result.contains("watch-one"));
        assertFalse(result.contains("watch-two"));
        assertTrue(result.startsWith("["));
        assertEquals("[]", AgentWatchLogUtils.tokens(null));
    }
}
