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

package com.alibaba.nacos.client.ai.utils;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aRadConverterTest {
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\u2003"})
    void absentVersionSelectsLatestAndKeepsBothSources(String version) throws Exception {
        AgentDiscoveryRequest request = request(version);
        assertEquals("latest", request.getReference().getLabel());
        assertNull(request.getReference().getVersion());
        assertEquals(Collections.singletonList("a2a"), request.getFilter().getProtocols());
        assertNull(request.getFilter().getEndpointSources());
        assertTrue(A2aRadConverter.project(snapshot("SERVICE"), request, null).isLatestVersion());
    }
    
    @Test
    void exactVersionAlwaysDegradesLatestFlag() throws Exception {
        AgentDiscoveryRequest request = request("1.0.0");
        assertNull(request.getReference().getLabel());
        assertEquals("1.0.0", request.getReference().getVersion());
        assertNull(A2aRadConverter.project(snapshot("URL"), request, null).isLatestVersion());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"URL", "SERVICE"})
    void queryOverrideDoesNotChangeStoredPreference(String type) throws Exception {
        AgentDiscoveryResult snapshot = snapshot(type);
        Endpoint runtime = endpoint("https://runtime.example/rpc", "JSONRPC", 0);
        runtime(snapshot).setEndpoints(Collections.singletonList(runtime));
        String before = JsonUtils.toJson(snapshot);
        AgentCardDetailInfo url = A2aRadConverter.project(snapshot, request(null), "url");
        AgentCardDetailInfo service = A2aRadConverter.project(snapshot, request(null), "service");
        AgentCardDetailInfo defaultCard = A2aRadConverter.project(snapshot, request(null), null);
        assertEquals(type, url.getRegistrationType());
        assertEquals(type, service.getRegistrationType());
        assertEquals("https://declared.example/rpc", url.getUrl());
        assertEquals(runtime.getUri(), service.getUrl());
        assertEquals("URL".equals(type) ? url.getUrl() : service.getUrl(), defaultCard.getUrl());
        assertEquals(before, JsonUtils.toJson(snapshot));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"URL", "SERVICE"})
    void emptySetsKeepPreferenceAndFallBackToCompleteCard(String type) throws Exception {
        AgentDiscoveryResult snapshot = snapshot(type);
        snapshot.getCallInterfaces().get(0).getEndpointSets()
            .forEach(set -> set.setEndpoints(Collections.emptyList()));
        AgentCardDetailInfo result = A2aRadConverter.project(snapshot, request(null), "SERVICE");
        assertEquals(type, result.getRegistrationType());
        assertEquals("https://declared.example/rpc", result.getUrl());
        assertEquals("full description", result.getDescription());
    }
    
    @Test
    void runtimeSortRetainsUnhealthyAndCompleteArraysAndPreferredTransport() throws Exception {
        AgentDiscoveryResult snapshot = snapshot("SERVICE");
        Endpoint disabled = endpoint("https://disabled.example/rpc", "JSONRPC", 0);
        disabled.setEnabled(false);
        Endpoint grpc = endpoint("https://grpc.example/rpc", "GRPC", 0);
        Endpoint last = endpoint("https://z.example/rpc", "JSONRPC", 2);
        Endpoint selected = endpoint("https://a.example/rpc", "JSONRPC", 2);
        selected.setHealthy(false);
        selected.setMetadata(new LinkedHashMap<String, String>());
        selected.getMetadata().put("__nacos.agent.endpoint.protocolVersion__", "1.1");
        selected.getMetadata().put("__nacos.agent.endpoint.tenant__", "tenant-a");
        runtime(snapshot).setEndpoints(Arrays.asList(last, disabled, selected, grpc));
        AgentCardDetailInfo card = A2aRadConverter.project(snapshot, request(null), null);
        assertEquals(3, card.getSupportedInterfaces().size());
        assertEquals(card.getSupportedInterfaces(), card.getAdditionalInterfaces());
        assertEquals(grpc.getUri(), card.getSupportedInterfaces().get(0).getUrl());
        assertEquals(selected.getUri(), card.getUrl());
        assertEquals("1.1", card.getProtocolVersion());
        assertEquals("tenant-a", card.getSupportedInterfaces().get(1).getTenant());
        assertEquals("0.3", card.getSupportedInterfaces().get(0).getProtocolVersion());
        assertNull(card.getSupportedInterfaces().get(0).getTenant());
        runtime(snapshot).setEndpoints(Collections.singletonList(grpc));
        assertEquals("GRPC",
            A2aRadConverter.project(snapshot, request(null), null).getPreferredTransport());
    }
    
    @Test
    void fullNativeCardSurvivesPublishAndDiscoveryWithoutMutatingInput() throws Exception {
        AgentCard original = card();
        String before = JsonUtils.toJson(original);
        AgentPublishRequest published =
            A2aRadConverter.publishRequest("public", original, null, false);
        assertFalse(published.isAutoSubmit());
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            published.getCallInterfaces().get(0).getEndpointSourceOrder());
        assertEquals("full description", published.getDescription());
        assertEquals(before, JsonUtils.toJson(original));
        AgentDiscoveryResult snapshot = snapshot("URL");
        snapshot.getCallInterfaces().get(0)
            .setNativeDescriptor(published.getCallInterfaces().get(0).getNativeDescriptor());
        AgentCardDetailInfo restored = A2aRadConverter.project(snapshot, request(null), null);
        assertEquals(original.getCapabilities(), restored.getCapabilities());
        assertEquals(original.getSkills(), restored.getSkills());
        assertEquals(original.getSecuritySchemes(), restored.getSecuritySchemes());
        assertEquals(original.getSecurity(), restored.getSecurity());
        assertEquals(original.getSecurityRequirements(), restored.getSecurityRequirements());
        assertEquals(original.getSignatures(), restored.getSignatures());
        assertEquals(original.getProvider(), restored.getProvider());
        assertEquals(original.getDocumentationUrl(), restored.getDocumentationUrl());
        assertEquals(original.getDefaultInputModes(), restored.getDefaultInputModes());
        assertEquals(original.getDefaultOutputModes(), restored.getDefaultOutputModes());
        assertNotSame(original.getCapabilities(), restored.getCapabilities());
    }
    
    @Test
    void v1CardNormalizesHistoricalFieldsAndDeduplicatesDeclaredNaturalKeys() throws Exception {
        AgentCard card = card();
        AgentInterface address = new AgentInterface();
        address.setUrl("https://declared.example/rpc");
        address.setProtocolBinding("HTTP+JSON");
        address.setProtocolVersion("1.0");
        card.setSupportedInterfaces(Arrays.asList(address, address));
        card.setUrl(null);
        card.setProtocolVersion(null);
        card.setPreferredTransport(null);
        AgentPublishRequest published = A2aRadConverter.publishRequest("public", card, "url", true);
        AgentCallInterface call = published.getCallInterfaces().get(0);
        assertTrue(published.isAutoSubmit());
        assertEquals("1.0", call.getProtocolVersion());
        assertEquals(1, call.getEndpointSets().get(0).getEndpoints().size());
        assertEquals("https://declared.example:443/rpc",
            call.getEndpointSets().get(0).getEndpoints().get(0).getUri());
        assertNull(card.getUrl());
        assertNull(address.getTransport());
    }
    
    @Test
    void rejectsBadCardAndSelectorsBeforePublication() {
        assertThrows(NacosException.class,
            () -> A2aRadConverter.publishRequest("public", null, null, false));
        assertThrows(NacosException.class,
            () -> A2aRadConverter.publishRequest("public", new AgentCard(), "URL", true));
        assertThrows(NacosException.class,
            () -> A2aRadConverter.publishRequest("public", card(), "wrong", true));
        assertThrows(NacosException.class,
            () -> A2aRadConverter.discoveryRequest("public", "", null));
        assertThrows(NacosException.class,
            () -> A2aRadConverter.discoveryRequest("public", "demo", "not-semver"));
    }
    
    @Test
    void missingInvalidMismatchedDescriptorIsNotFound() throws Exception {
        AgentDiscoveryResult snapshot = snapshot("URL");
        List<Object> invalid = Arrays.asList(null, "not-json", Collections.emptyMap(),
            Collections.singletonMap("name", "demo"));
        for (Object descriptor : invalid) {
            snapshot.getCallInterfaces().get(0).setNativeDescriptor(descriptor);
            assertNotFound(snapshot);
        }
        snapshot = snapshot("URL");
        snapshot.setVersion("2.0.0");
        assertNotFound(snapshot);
        snapshot = snapshot("URL");
        snapshot.setAgentName("other");
        assertNotFound(snapshot);
        snapshot = snapshot("URL");
        snapshot.setNamespaceId("other");
        assertNotFound(snapshot);
        snapshot = snapshot("URL");
        snapshot.getCallInterfaces().get(0).setProtocol("other");
        assertNotFound(snapshot);
        snapshot.setCallInterfaces(Collections.emptyList());
        assertNotFound(snapshot);
        assertNotFound(null);
    }
    
    @Test
    void malformedSourceOrderAndInvalidPublicMetadataCannotSilentlyFallBack() throws Exception {
        AgentDiscoveryResult snapshot = snapshot("SERVICE");
        snapshot.getCallInterfaces().get(0)
            .setEndpointSets(Collections.singletonList(runtime(snapshot)));
        assertNotFound(snapshot);
        snapshot = snapshot("SERVICE");
        Endpoint endpoint = endpoint("https://runtime.example/rpc", "JSONRPC", 0);
        endpoint
            .setMetadata(Collections.singletonMap("__nacos.agent.endpoint.protocolVersion__", ""));
        runtime(snapshot).setEndpoints(Collections.singletonList(endpoint));
        assertNotFound(snapshot);
    }
    
    private void assertNotFound(AgentDiscoveryResult snapshot) throws Exception {
        AgentDiscoveryRequest request = request(null);
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> A2aRadConverter.project(snapshot, request, null)).getErrCode());
    }
    
    private AgentDiscoveryRequest request(String version) throws Exception {
        return A2aRadConverter.discoveryRequest("public", "demo", version);
    }
    
    private AgentDiscoveryResult snapshot(String type) throws Exception {
        AgentPublishRequest publish = A2aRadConverter.publishRequest("public", card(), type, false);
        AgentCallInterface call = publish.getCallInterfaces().get(0);
        EndpointSet runtime = new EndpointSet();
        runtime.setSource(EndpointSource.RUNTIME);
        runtime.setEndpoints(new ArrayList<Endpoint>());
        EndpointSet declared = call.getEndpointSets().get(0);
        call.setEndpointSets("URL".equals(type) ? Arrays.asList(declared, runtime)
            : Arrays.asList(runtime, declared));
        call.setEndpointSourceOrder(null);
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("demo");
        result.setVersion("1.0.0");
        result.setCallInterfaces(Collections.singletonList(call));
        return result;
    }
    
    private EndpointSet runtime(AgentDiscoveryResult snapshot) {
        return snapshot.getCallInterfaces().get(0).getEndpointSets().stream()
            .filter(set -> set.getSource() == EndpointSource.RUNTIME).findFirst().get();
    }
    
    private Endpoint endpoint(String uri, String transport, int priority) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport(transport);
        endpoint.setPriority(priority);
        return endpoint;
    }
    
    private AgentCard card() {
        return JsonUtils.toObj("{\"name\":\"demo\",\"version\":\"1.0.0\","
            + "\"protocolVersion\":\"0.3\",\"url\":\"https://declared.example/rpc\","
            + "\"preferredTransport\":\"JSONRPC\",\"description\":\"full description\","
            + "\"capabilities\":{\"streaming\":true,\"extendedAgentCard\":true},"
            + "\"skills\":[{\"id\":\"s\",\"name\":\"skill\",\"tags\":[\"tag\"]}],"
            + "\"provider\":{\"organization\":\"Nacos\",\"url\":\"https://nacos.io\"},"
            + "\"securitySchemes\":{\"token\":{\"type\":\"http\",\"scheme\":\"bearer\"}},"
            + "\"security\":[{\"token\":[]}],\"securityRequirements\":[{\"token\":[]}],"
            + "\"signatures\":[{\"protected\":\"header\",\"signature\":\"signature\"}],"
            + "\"documentationUrl\":\"https://docs.example\","
            + "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"application/json\"]}",
            AgentCard.class);
    }
}
