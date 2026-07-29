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

package com.alibaba.nacos.api.ai.model.agent;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContractModelTest extends BasicRequestTest {
    
    @Test
    void testEndpointOptionalValuesAreNotSerialized() throws JsonProcessingException {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://example.com/agent");
        endpoint.setTransport("JSON-RPC");
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        
        String json = mapper.writeValueAsString(endpoint);
        assertFalse(json.contains("effectivePriority"));
        assertFalse(json.contains("effectiveWeight"));
        assertFalse(json.contains("priority"));
        assertFalse(json.contains("weight"));
        assertFalse(json.contains("healthy"));
        
        Endpoint deserialized = mapper.readValue(json, Endpoint.class);
        assertEquals("https://example.com/agent", deserialized.getUri());
        assertEquals("JSON-RPC", deserialized.getTransport());
        assertEquals("cn-hangzhou-a", deserialized.getMetadata().get("zone"));
    }
    
    @Test
    void testEndpointExplicitValuesRoundTrip() throws JsonProcessingException {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("http://127.0.0.1:8080/a2a");
        endpoint.setTransport("JSON-RPC");
        endpoint.setPriority(10);
        endpoint.setWeight(2.5D);
        endpoint.setHealthy(false);
        
        Endpoint deserialized =
            mapper.readValue(mapper.writeValueAsString(endpoint), Endpoint.class);
        assertEquals(Integer.valueOf(10), deserialized.getPriority());
        assertEquals(Double.valueOf(2.5D), deserialized.getWeight());
        assertEquals(Boolean.FALSE, deserialized.getHealthy());
    }
    
    @Test
    void testDefaultEnumWireValuesAreExact() throws JsonProcessingException {
        assertEquals("\"RUNTIME\"", mapper.writeValueAsString(EndpointSource.RUNTIME));
        assertEquals("\"UNHEALTHY\"", mapper.writeValueAsString(RuntimeEndpointState.UNHEALTHY));
        
        assertThrows(JsonProcessingException.class,
            () -> mapper.readValue("\"runtime\"", EndpointSource.class));
    }
    
    @Test
    void testNativeDescriptorNullIsBoundForControllerValidation() throws JsonProcessingException {
        String json = "{\"protocol\":\"a2a\",\"descriptorMediaType\":\"application/json\","
            + "\"nativeDescriptor\":null,\"endpointSourceOrder\":[\"DECLARED\"]}";
        
        AgentCallInterface callInterface = mapper.readValue(json, AgentCallInterface.class);
        assertNull(callInterface.getNativeDescriptor());
    }
    
    @Test
    void testAgentRoundTripWithAllFields() throws JsonProcessingException {
        Agent restored = roundTrip(newAgent(), Agent.class);
        
        assertEquals("public", restored.getNamespaceId());
        assertEquals("Demo Agent", restored.getAgentName());
        assertEquals("Demo Agent Display", restored.getDisplayName());
        assertEquals("Agent description", restored.getDescription());
        assertEquals("https://example.com/icon.png", restored.getIconUrl());
        assertEquals("Nacos", restored.getProvider().getName());
        assertEquals("https://nacos.io", restored.getProvider().getUrl());
        assertEquals(Arrays.asList("assistant", "demo"), restored.getTags());
        assertEquals("blue", restored.getExtensions().get("example.com/color"));
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_ENABLE, restored.getStatus());
        assertEquals("nacos", restored.getOwner());
        assertEquals("PUBLIC", restored.getScope());
        assertVersionInfo(restored.getVersionInfo());
        assertVersionCatalog(restored.getVersionCatalog());
        assertEquals(Long.valueOf(3L), restored.getMetaVersion());
        assertEquals(Long.valueOf(1L), restored.getCreateTime());
        assertEquals(Long.valueOf(2L), restored.getUpdateTime());
    }
    
    @Test
    void testAgentSummaryRoundTripWithAllFields() throws JsonProcessingException {
        AgentSummary summary = new AgentSummary();
        summary.setNamespaceId("public");
        summary.setAgentName("Demo Agent");
        summary.setDisplayName("Demo Agent Display");
        summary.setDescription("Agent summary description");
        summary.setIconUrl("https://example.com/icon.png");
        summary.setProvider(newProvider());
        summary.setTags(Arrays.asList("assistant", "demo"));
        summary.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        summary.setOwner("nacos");
        summary.setScope("PUBLIC");
        summary.setVersionInfo(newVersionInfo());
        summary.setVersionCatalog(newVersionCatalog());
        summary.setMetaVersion(3L);
        summary.setCreateTime(1L);
        summary.setUpdateTime(2L);
        
        AgentSummary restored = roundTrip(summary, AgentSummary.class);
        assertEquals("public", restored.getNamespaceId());
        assertEquals("Demo Agent", restored.getAgentName());
        assertEquals("Demo Agent Display", restored.getDisplayName());
        assertEquals("Agent summary description", restored.getDescription());
        assertEquals("https://example.com/icon.png", restored.getIconUrl());
        assertEquals("Nacos", restored.getProvider().getName());
        assertEquals("https://nacos.io", restored.getProvider().getUrl());
        assertEquals(Arrays.asList("assistant", "demo"), restored.getTags());
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_ENABLE, restored.getStatus());
        assertEquals("nacos", restored.getOwner());
        assertEquals("PUBLIC", restored.getScope());
        assertVersionInfo(restored.getVersionInfo());
        assertVersionCatalog(restored.getVersionCatalog());
        assertEquals(Long.valueOf(3L), restored.getMetaVersion());
        assertEquals(Long.valueOf(1L), restored.getCreateTime());
        assertEquals(Long.valueOf(2L), restored.getUpdateTime());
    }
    
    @Test
    void testAgentOverviewRoundTripWithCompletePage() throws JsonProcessingException {
        Page<AgentVersionSummary> versionPage = new Page<AgentVersionSummary>();
        versionPage.setTotalCount(1);
        versionPage.setPageNumber(1);
        versionPage.setPagesAvailable(1);
        versionPage.setPageItems(Collections.singletonList(newVersionSummary()));
        AgentOverview overview = new AgentOverview();
        overview.setAgent(newAgent());
        overview.setVersionPage(versionPage);
        
        AgentOverview restored = roundTrip(overview, AgentOverview.class);
        assertEquals("Demo Agent", restored.getAgent().getAgentName());
        assertEquals(1, restored.getVersionPage().getTotalCount());
        assertEquals(1, restored.getVersionPage().getPageNumber());
        assertEquals(1, restored.getVersionPage().getPagesAvailable());
        assertVersionSummary(restored.getVersionPage().getPageItems().get(0));
    }
    
    @Test
    void testAgentVersionDetailRoundTripWithCallInterface() throws JsonProcessingException {
        AgentVersionDetail detail = new AgentVersionDetail();
        detail.setNamespaceId("public");
        detail.setAgentName("Demo Agent");
        detail.setVersion("1.0.0-RC1");
        detail.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        detail.setCallInterfaces(Collections.singletonList(newCallInterface()));
        detail.setAuthor("nacos");
        detail.setChangeDescription("Initial online version");
        detail.setContentDigest(contentDigest());
        detail.setCreateTime(1L);
        detail.setUpdateTime(2L);
        
        AgentVersionDetail restored = roundTrip(detail, AgentVersionDetail.class);
        assertEquals("public", restored.getNamespaceId());
        assertEquals("Demo Agent", restored.getAgentName());
        assertEquals("1.0.0-RC1", restored.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, restored.getStatus());
        assertEquals("nacos", restored.getAuthor());
        assertEquals("Initial online version", restored.getChangeDescription());
        assertEquals(contentDigest(), restored.getContentDigest());
        assertEquals(Long.valueOf(1L), restored.getCreateTime());
        assertEquals(Long.valueOf(2L), restored.getUpdateTime());
        assertCallInterface(restored.getCallInterfaces().get(0));
    }
    
    @Test
    void testRuntimeEndpointSnapshotRoundTripWithAllFields() throws JsonProcessingException {
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.6");
        binding.setVersionRange("[1.0.0,2.0.0)");
        RuntimeEndpointSnapshotItem item = new RuntimeEndpointSnapshotItem();
        item.setEndpoint(newEndpoint("https://runtime.example.com:443/a2a", true));
        item.setBindings(Collections.singletonList(binding));
        item.setState(RuntimeEndpointState.AVAILABLE);
        item.setEnabled(true);
        item.setHealthy(true);
        item.setLastUpdatedTime(2L);
        RuntimeEndpointSnapshot snapshot = new RuntimeEndpointSnapshot();
        snapshot.setNamespaceId("public");
        snapshot.setAgentName("Demo Agent");
        snapshot.setProtocol("a2a");
        snapshot.setVersion("1.0.0");
        snapshot.setItems(Collections.singletonList(item));
        
        RuntimeEndpointSnapshot restored = roundTrip(snapshot, RuntimeEndpointSnapshot.class);
        assertEquals("public", restored.getNamespaceId());
        assertEquals("Demo Agent", restored.getAgentName());
        assertEquals("a2a", restored.getProtocol());
        assertEquals("1.0.0", restored.getVersion());
        RuntimeEndpointSnapshotItem restoredItem = restored.getItems().get(0);
        assertEndpoint(restoredItem.getEndpoint(), true);
        assertEquals("1.0.6", restoredItem.getBindings().get(0).getRuntimeVersion());
        assertEquals("[1.0.0,2.0.0)", restoredItem.getBindings().get(0).getVersionRange());
        assertEquals(RuntimeEndpointState.AVAILABLE, restoredItem.getState());
        assertEquals(Boolean.TRUE, restoredItem.getEnabled());
        assertEquals(Boolean.TRUE, restoredItem.getHealthy());
        assertEquals(Long.valueOf(2L), restoredItem.getLastUpdatedTime());
    }
    
    @Test
    void testAdminRequestModelsDoNotCarryNamespace() throws JsonProcessingException {
        AgentUpdateRequest update = new AgentUpdateRequest();
        update.setAgentName("Demo Agent");
        update.setStatus(AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        update.validate();
        
        AgentVersionCommand command = new AgentVersionCommand();
        command.setAgentName("Demo Agent");
        command.setVersion("1.0.0");
        command.validate();
        
        assertFalse(mapper.writeValueAsString(update).contains("namespaceId"));
        assertFalse(mapper.writeValueAsString(command).contains("namespaceId"));
    }
    
    @Test
    void testAgentUpdateRequestRequiresResourceStatus() {
        AgentUpdateRequest request = new AgentUpdateRequest();
        request.setAgentName("Demo Agent");
        request.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        
        request.setStatus(null);
        assertThrows(IllegalArgumentException.class, request::validate);
        request.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        request.validate();
    }
    
    @Test
    void testDraftCreateRequiresExactlyOneContentSource() {
        AgentDraftCreateRequest request = new AgentDraftCreateRequest();
        request.setAgentName("Demo Agent");
        request.setVersion("2.0.0");
        
        assertThrows(IllegalArgumentException.class, request::validate);
        
        request.setCallInterfaces(Collections.singletonList(newCallInterface()));
        request.setBasedOnVersion("1.0.0");
        assertThrows(IllegalArgumentException.class, request::validate);
        
        request.setBasedOnVersion(null);
        request.validate();
        
        request.setCallInterfaces(null);
        request.setBasedOnVersion("1.0.0");
        request.validate();
    }
    
    @Test
    void testLabelsUpdateRejectsLatestLabel() {
        AgentLabelsUpdateRequest request = new AgentLabelsUpdateRequest();
        request.setAgentName("Demo Agent");
        request.setLabels(Collections.singletonMap("latest", "1.0.0"));
        
        assertThrows(IllegalArgumentException.class, request::validate);
        
        request.setLabels(Collections.singletonMap("stable", "1.0.0"));
        request.validate();
    }
    
    @Test
    void testDraftCreateRequestAccessors() {
        AgentDraftCreateRequest request = new AgentDraftCreateRequest();
        AgentProvider provider = newProvider();
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        extensions.put("region", "east");
        request.setAgentName("Demo Agent");
        request.setDisplayName("Demo");
        request.setDescription("description");
        request.setIconUrl("https://example.com/icon.png");
        request.setProvider(provider);
        request.setTags(Collections.singletonList("assistant"));
        request.setExtensions(extensions);
        request.setVersion("2.0.0");
        request.setCallInterfaces(Collections.singletonList(newCallInterface()));
        request.setAuthor("alice");
        request.setChangeDescription("initial draft");
        request.setBasedOnVersion(null);
        
        request.validate();
        
        assertEquals("Demo Agent", request.getAgentName());
        assertEquals("Demo", request.getDisplayName());
        assertEquals("description", request.getDescription());
        assertEquals("https://example.com/icon.png", request.getIconUrl());
        assertEquals(provider, request.getProvider());
        assertEquals(Collections.singletonList("assistant"), request.getTags());
        assertEquals(extensions, request.getExtensions());
        assertEquals("2.0.0", request.getVersion());
        assertEquals("a2a", request.getCallInterfaces().get(0).getProtocol());
        assertEquals("alice", request.getAuthor());
        assertEquals("initial draft", request.getChangeDescription());
        assertNull(request.getBasedOnVersion());
    }
    
    @Test
    void testDraftUpdateRequestAccessorsAndValidation() {
        AgentDraftUpdateRequest request = new AgentDraftUpdateRequest();
        request.setAgentName("Demo Agent");
        request.setVersion("2.0.0");
        request.setCallInterfaces(Collections.singletonList(newCallInterface()));
        request.setChangeDescription("updated");
        
        request.validate();
        
        assertEquals("Demo Agent", request.getAgentName());
        assertEquals("2.0.0", request.getVersion());
        assertEquals("a2a", request.getCallInterfaces().get(0).getProtocol());
        assertEquals("updated", request.getChangeDescription());
        
        request.setCallInterfaces(null);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testAgentUpdateRequestAccessorsAndEveryWritableStatus() {
        AgentUpdateRequest request = new AgentUpdateRequest();
        AgentProvider provider = newProvider();
        Map<String, Object> extensions = Collections.<String, Object>singletonMap("region", "east");
        request.setAgentName("Demo Agent");
        request.setDisplayName("Demo");
        request.setDescription("description");
        request.setIconUrl("https://example.com/icon.png");
        request.setProvider(provider);
        request.setTags(Collections.singletonList("assistant"));
        request.setExtensions(extensions);
        request.setStatus(AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        
        request.validate();
        
        assertEquals("Demo Agent", request.getAgentName());
        assertEquals("Demo", request.getDisplayName());
        assertEquals("description", request.getDescription());
        assertEquals("https://example.com/icon.png", request.getIconUrl());
        assertEquals(provider, request.getProvider());
        assertEquals(Collections.singletonList("assistant"), request.getTags());
        assertEquals(extensions, request.getExtensions());
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_DISABLE, request.getStatus());
    }
    
    @Test
    void testLabelsUpdateRequestAccessorsAndNullLabels() {
        AgentLabelsUpdateRequest request = new AgentLabelsUpdateRequest();
        request.setAgentName("Demo Agent");
        request.setLabels(null);
        
        assertEquals("Demo Agent", request.getAgentName());
        assertNull(request.getLabels());
        assertThrows(IllegalArgumentException.class, request::validate);
        
        Map<String, String> labels = Collections.singletonMap("stable", "1.0.0");
        request.setLabels(labels);
        request.validate();
        assertEquals(labels, request.getLabels());
    }
    
    @Test
    void testAdminRequestBlankUtility() {
        assertTrue(AgentAdminRequestUtils.isBlank(null));
        assertTrue(AgentAdminRequestUtils.isBlank(""));
        assertTrue(AgentAdminRequestUtils.isBlank(" \t"));
        assertFalse(AgentAdminRequestUtils.isBlank(" value"));
    }
    
    private Agent newAgent() {
        Agent agent = new Agent();
        agent.setNamespaceId("public");
        agent.setAgentName("Demo Agent");
        agent.setDisplayName("Demo Agent Display");
        agent.setDescription("Agent description");
        agent.setIconUrl("https://example.com/icon.png");
        agent.setProvider(newProvider());
        agent.setTags(Arrays.asList("assistant", "demo"));
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        extensions.put("example.com/color", "blue");
        agent.setExtensions(extensions);
        agent.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        agent.setOwner("nacos");
        agent.setScope("PUBLIC");
        agent.setVersionInfo(newVersionInfo());
        agent.setVersionCatalog(newVersionCatalog());
        agent.setMetaVersion(3L);
        agent.setCreateTime(1L);
        agent.setUpdateTime(2L);
        return agent;
    }
    
    private AgentProvider newProvider() {
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        return provider;
    }
    
    private AgentVersionInfo newVersionInfo() {
        AgentVersionInfo versionInfo = new AgentVersionInfo();
        versionInfo.setEditingVersion("2.0.0");
        versionInfo.setReviewingVersion("2.1.0");
        versionInfo.setOnlineCnt(1);
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("latest", "1.0.0");
        labels.put("stable", "1.0.0");
        versionInfo.setLabels(labels);
        return versionInfo;
    }
    
    private AgentVersionCatalog newVersionCatalog() {
        AgentVersionCatalogEntry entry = new AgentVersionCatalogEntry();
        entry.setVersion("1.0.0");
        entry.setLabels(Collections.singletonList("stable"));
        entry.setProtocols(Collections.singletonList("a2a"));
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setLatestVersion("1.0.0");
        catalog.setOnlineVersions(Collections.singletonList(entry));
        return catalog;
    }
    
    private AgentVersionSummary newVersionSummary() {
        AgentVersionSummary summary = new AgentVersionSummary();
        summary.setVersion("1.0.0");
        summary.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        summary.setAuthor("nacos");
        summary.setChangeDescription("Initial online version");
        summary.setContentDigest(contentDigest());
        summary.setCreateTime(1L);
        summary.setUpdateTime(2L);
        return summary;
    }
    
    private AgentCallInterface newCallInterface() {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("1.0.0");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("name", "Demo Agent"));
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        callInterface.setDeclaredEndpoints(Collections.singletonList(
            newEndpoint("https://declared.example.com:443/a2a", null)));
        return callInterface;
    }
    
    private Endpoint newEndpoint(String uri, Boolean healthy) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport("JSON-RPC");
        endpoint.setPriority(1);
        endpoint.setWeight(2.5D);
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        endpoint.setHealthy(healthy);
        return endpoint;
    }
    
    private void assertVersionInfo(AgentVersionInfo versionInfo) {
        assertEquals("2.0.0", versionInfo.getEditingVersion());
        assertEquals("2.1.0", versionInfo.getReviewingVersion());
        assertEquals(Integer.valueOf(1), versionInfo.getOnlineCnt());
        assertEquals("1.0.0", versionInfo.getLabels().get("latest"));
        assertEquals("1.0.0", versionInfo.getLabels().get("stable"));
    }
    
    private void assertVersionCatalog(AgentVersionCatalog catalog) {
        assertEquals("1.0.0", catalog.getLatestVersion());
        AgentVersionCatalogEntry entry = catalog.getOnlineVersions().get(0);
        assertEquals("1.0.0", entry.getVersion());
        assertEquals(Collections.singletonList("stable"), entry.getLabels());
        assertEquals(Collections.singletonList("a2a"), entry.getProtocols());
    }
    
    private void assertVersionSummary(AgentVersionSummary summary) {
        assertEquals("1.0.0", summary.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, summary.getStatus());
        assertEquals("nacos", summary.getAuthor());
        assertEquals("Initial online version", summary.getChangeDescription());
        assertEquals(contentDigest(), summary.getContentDigest());
        assertEquals(Long.valueOf(1L), summary.getCreateTime());
        assertEquals(Long.valueOf(2L), summary.getUpdateTime());
    }
    
    @SuppressWarnings("unchecked")
    private void assertCallInterface(AgentCallInterface callInterface) {
        assertEquals("a2a", callInterface.getProtocol());
        assertEquals("1.0.0", callInterface.getProtocolVersion());
        assertEquals("application/json", callInterface.getDescriptorMediaType());
        assertEquals("Demo Agent",
            ((Map<String, Object>) callInterface.getNativeDescriptor()).get("name"));
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            callInterface.getEndpointSourceOrder());
        assertEndpoint(callInterface.getDeclaredEndpoints().get(0), null);
    }
    
    private void assertEndpoint(Endpoint endpoint, Boolean healthy) {
        assertEquals(healthy == null ? "https://declared.example.com:443/a2a"
            : "https://runtime.example.com:443/a2a", endpoint.getUri());
        assertEquals("JSON-RPC", endpoint.getTransport());
        assertEquals(Integer.valueOf(1), endpoint.getPriority());
        assertEquals(Double.valueOf(2.5D), endpoint.getWeight());
        assertEquals("cn-hangzhou-a", endpoint.getMetadata().get("zone"));
        assertEquals(healthy, endpoint.getHealthy());
    }
    
    private String contentDigest() {
        return "sha256:0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";
    }
    
    private <T> T roundTrip(T value, Class<T> type) throws JsonProcessingException {
        String json = mapper.writeValueAsString(value);
        assertNotNull(json);
        return mapper.readValue(json, type);
    }
}
