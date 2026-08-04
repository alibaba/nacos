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

package com.alibaba.nacos.api.ai.model.rad;

import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.model.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RadProtocolModelTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void shouldRoundTripSearchRequestAndCatalogPage() throws Exception {
        AgentSearchRequest request = new AgentSearchRequest();
        request.setNamespaceId("public");
        request.setAgentNameContains("Order Agent");
        request.setTagsAll(Arrays.asList("commerce", "order"));
        request.setProtocolsAny(Collections.singletonList("a2a"));
        request.setPageNo(1);
        request.setPageSize(20);
        
        AgentSearchRequest restoredRequest =
            objectMapper.readValue(objectMapper.writeValueAsBytes(request),
                AgentSearchRequest.class);
        assertEquals("public", restoredRequest.getNamespaceId());
        assertEquals("Order Agent", restoredRequest.getAgentNameContains());
        assertEquals(Arrays.asList("commerce", "order"), restoredRequest.getTagsAll());
        assertEquals(Collections.singletonList("a2a"), restoredRequest.getProtocolsAny());
        assertEquals(1, restoredRequest.getPageNo());
        assertEquals(20, restoredRequest.getPageSize());
        
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        AgentCatalogVersion version = new AgentCatalogVersion();
        version.setVersion("1.0.0-RC1");
        version.setLabels(Collections.singletonList("stable"));
        version.setProtocols(Collections.singletonList("a2a"));
        AgentCatalogEntry entry = new AgentCatalogEntry();
        entry.setAgentName("Order Agent");
        entry.setDisplayName("Order Agent");
        entry.setDescription("Handles orders");
        entry.setIconUrl("https://nacos.io/order-agent.png");
        entry.setProvider(provider);
        entry.setTags(Arrays.asList("commerce", "order"));
        entry.setLatestVersion("1.0.0-RC1");
        entry.setVersions(Collections.singletonList(version));
        Page<AgentCatalogEntry> page = new Page<>();
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        page.setPageItems(Collections.singletonList(entry));
        
        byte[] json = objectMapper.writeValueAsBytes(page);
        Page<AgentCatalogEntry> restoredPage = objectMapper.readValue(json,
            new TypeReference<Page<AgentCatalogEntry>>() {
            });
        assertEquals(1, restoredPage.getTotalCount());
        assertEquals("Order Agent", restoredPage.getPageItems().get(0).getAgentName());
        assertEquals("Nacos", restoredPage.getPageItems().get(0).getProvider().getName());
        assertEquals("1.0.0-RC1",
            restoredPage.getPageItems().get(0).getVersions().get(0).getVersion());
    }
    
    @Test
    void shouldRoundTripDiscoveryRequestAndResult() throws Exception {
        AgentReference reference = new AgentReference();
        reference.setAgentName("Order Agent");
        reference.setLabel("latest");
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.singletonList("a2a"));
        filter.setTransports(Collections.singletonList("JSONRPC"));
        filter.setEndpointSources(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        filter.setMetadataSelector(Collections.singletonMap("zone", "cn-hangzhou-h"));
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        request.setFilter(filter);
        
        JsonNode requestJson = objectMapper.readTree(objectMapper.writeValueAsBytes(request));
        assertEquals("latest", requestJson.path("reference").path("label").asText());
        assertFalse(requestJson.path("reference").has("version"));
        AgentDiscoveryRequest restoredRequest =
            objectMapper.treeToValue(requestJson, AgentDiscoveryRequest.class);
        assertEquals(EndpointSource.RUNTIME,
            restoredRequest.getFilter().getEndpointSources().get(0));
        
        AgentDiscoveryEndpoint endpoint = new AgentDiscoveryEndpoint();
        endpoint.setUri("https://10.0.0.8:8443/a2a");
        endpoint.setTransport("JSONRPC");
        endpoint.setPriority(0);
        endpoint.setWeight(1.0D);
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-h"));
        endpoint.setHealthy(false);
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.6");
        binding.setVersionRange("[1.0.0,2.0.0)");
        endpoint.setBindings(Collections.singletonList(binding));
        EndpointSet endpointSet = new EndpointSet();
        endpointSet.setSource(EndpointSource.RUNTIME);
        endpointSet.setSourceRevision("murmur3-x64-128-v1:0123456789abcdef0123456789abcdef");
        endpointSet.setEndpoints(Collections.singletonList(endpoint));
        AgentDiscoveryCallInterface callInterface = new AgentDiscoveryCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("1.0");
        callInterface.setDescriptorMediaType("application/json");
        Map<String, Object> nativeDescriptor = new LinkedHashMap<>();
        nativeDescriptor.put("name", "Order Agent");
        nativeDescriptor.put("version", "1.0.6");
        callInterface.setNativeDescriptor(nativeDescriptor);
        callInterface.setEndpointSets(Collections.singletonList(endpointSet));
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("Order Agent");
        result.setVersion("1.0.6");
        result.setContentDigest(
            "sha256:1111111111111111111111111111111111111111111111111111111111111111");
        result.setCallInterfaces(Collections.singletonList(callInterface));
        
        AgentDiscoveryResult restoredResult =
            objectMapper.readValue(objectMapper.writeValueAsBytes(result),
                AgentDiscoveryResult.class);
        assertEquals("1.0.6", restoredResult.getVersion());
        assertEquals("a2a", restoredResult.getCallInterfaces().get(0).getProtocol());
        assertNotNull(restoredResult.getCallInterfaces().get(0).getNativeDescriptor());
        AgentDiscoveryEndpoint restoredEndpoint =
            restoredResult.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints()
                .get(0);
        assertEquals(Boolean.FALSE, restoredEndpoint.getHealthy());
        assertEquals("cn-hangzhou-h", restoredEndpoint.getMetadata().get("zone"));
        assertEquals("1.0.6", restoredEndpoint.getBindings().get(0).getRuntimeVersion());
    }
    
    @Test
    void shouldRoundTripExactVersionDiscoveryRequest() throws Exception {
        AgentReference reference = new AgentReference();
        reference.setAgentName("Order Agent");
        reference.setVersion("1.0.0-RC1");
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocolVersion("1.0");
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        request.setFilter(filter);
        
        AgentDiscoveryRequest restored = objectMapper.readValue(
            objectMapper.writeValueAsBytes(request), AgentDiscoveryRequest.class);
        assertEquals("public", restored.getNamespaceId());
        assertEquals("Order Agent", restored.getReference().getAgentName());
        assertEquals("1.0.0-RC1", restored.getReference().getVersion());
        assertNull(restored.getReference().getLabel());
        assertEquals("1.0", restored.getFilter().getProtocolVersion());
    }
    
    @Test
    void shouldRoundTripEndpointBatches() throws Exception {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://10.0.0.8:8443/a2a");
        endpoint.setTransport("JSONRPC");
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-h"));
        AgentEndpointRegistrationBatch registration = new AgentEndpointRegistrationBatch();
        registration.setNamespaceId("public");
        registration.setAgentName("Order Agent");
        registration.setRuntimeVersion("1.0.6");
        registration.setVersionRange("[1.0.0,2.0.0)");
        registration.setProtocol("a2a");
        registration.setEndpoints(Collections.singletonList(endpoint));
        
        JsonNode registrationJson =
            objectMapper.readTree(objectMapper.writeValueAsBytes(registration));
        assertEquals("[1.0.0,2.0.0)", registrationJson.path("versionRange").asText());
        assertFalse(registrationJson.path("endpoints").get(0).has("healthy"));
        AgentEndpointRegistrationBatch restoredRegistration =
            objectMapper.treeToValue(registrationJson,
                AgentEndpointRegistrationBatch.class);
        assertEquals("1.0.6", restoredRegistration.getRuntimeVersion());
        assertNull(restoredRegistration.getEndpoints().get(0).getHealthy());
        
        Endpoint endpointKey = new Endpoint();
        endpointKey.setUri(endpoint.getUri());
        endpointKey.setTransport(endpoint.getTransport());
        AgentEndpointDeregistrationBatch deregistration = new AgentEndpointDeregistrationBatch();
        deregistration.setNamespaceId("public");
        deregistration.setAgentName("Order Agent");
        deregistration.setProtocol("a2a");
        deregistration.setEndpoints(Collections.singletonList(endpointKey));
        
        JsonNode deregistrationJson =
            objectMapper.readTree(objectMapper.writeValueAsBytes(deregistration));
        assertEquals(2, deregistrationJson.path("endpoints").get(0).size());
        AgentEndpointDeregistrationBatch restoredDeregistration =
            objectMapper.treeToValue(deregistrationJson,
                AgentEndpointDeregistrationBatch.class);
        assertEquals("JSONRPC", restoredDeregistration.getEndpoints().get(0).getTransport());
    }
    
    @Test
    void shouldBindExplicitJsonNullForControllerValidation() throws JsonProcessingException {
        String json = "{\"protocol\":\"a2a\",\"descriptorMediaType\":\"application/json\","
            + "\"nativeDescriptor\":null,\"endpointSets\":[]}";
        
        AgentDiscoveryCallInterface callInterface =
            objectMapper.readValue(json, AgentDiscoveryCallInterface.class);
        assertNull(callInterface.getNativeDescriptor());
    }
}
