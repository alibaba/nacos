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

import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;

import com.alibaba.nacos.api.ai.model.agent.admin.AgentVersionRequest;

import com.alibaba.nacos.api.ai.model.agent.admin.AgentLabelsUpdateRequest;

import com.alibaba.nacos.api.ai.model.agent.admin.AgentUpdateRequest;

import com.alibaba.nacos.api.ai.model.agent.admin.AgentDraftUpdateRequest;

import com.alibaba.nacos.api.ai.model.agent.admin.AgentDraftCreateRequest;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.base.AbstractAgentMetadata;
import com.alibaba.nacos.api.ai.model.agent.base.AbstractAgentDraftRequest;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
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
    void shouldSerializeDefinitionThroughSourceEndpointSets() throws JsonProcessingException {
        com.fasterxml.jackson.databind.JsonNode json = mapper.valueToTree(newCallInterface());
        assertEquals("DECLARED", json.at("/endpointSets/0/source").asText());
        assertEquals("https://declared.example.com:443/a2a",
            json.at("/endpointSets/0/endpoints/0/uri").asText());
        assertFalse(json.has("declaredEndpoints"));
        assertEquals("RUNTIME", json.at("/endpointSourceOrder/0").asText());
    }
    
    @Test
    void testEndpointDefaultsAreSerializedAndReferenceNullsFollowMapperPolicy()
        throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://example.com/agent");
        endpoint.setTransport("JSON-RPC");
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        
        String json = mapper.writeValueAsString(endpoint);
        assertFalse(json.contains("effectivePriority"));
        assertFalse(json.contains("effectiveWeight"));
        assertEquals(0, mapper.readTree(json).get("priority").asInt());
        assertEquals(1D, mapper.readTree(json).get("weight").asDouble());
        assertTrue(mapper.readTree(json).get("healthy").asBoolean());
        assertTrue(mapper.readTree(json).get("enabled").asBoolean());
        assertFalse(mapper.readTree(json).has("state"));
        
        Endpoint deserialized = mapper.readValue(json, Endpoint.class);
        assertEquals("https://example.com/agent", deserialized.getUri());
        assertEquals("JSON-RPC", deserialized.getTransport());
        assertEquals("cn-hangzhou-a", deserialized.getMetadata().get("zone"));
    }
    
    @Test
    void testMissingEndpointFieldsUseDefaultsAndFalseAndZeroSurviveJson() throws Exception {
        Endpoint endpoint = mapper.readValue(
            "{\"uri\":\"https://example.com:443/a2a\",\"transport\":\"HTTP\"}", Endpoint.class);
        assertEquals(0, endpoint.getPriority());
        assertEquals(1D, endpoint.getWeight());
        assertTrue(endpoint.getHealthy());
        assertTrue(endpoint.getEnabled());
        endpoint.setWeight(0D);
        endpoint.setHealthy(false);
        endpoint.setEnabled(false);
        Endpoint restored = mapper.readValue(mapper.writeValueAsString(endpoint), Endpoint.class);
        assertEquals(0D, restored.getWeight());
        assertFalse(restored.getHealthy());
        assertFalse(restored.getEnabled());
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(
            "{\"healthy\":\"not-a-boolean\"}", Endpoint.class));
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
        
        assertThrows(JsonProcessingException.class,
            () -> mapper.readValue("\"runtime\"", EndpointSource.class));
    }
    
    @Test
    void testNativeDescriptorNullIsBoundForControllerValidation() throws JsonProcessingException {
        String json = "{\"protocol\":\"a2a\",\"descriptorMediaType\":\"application/json\","
            + "\"nativeDescriptor\":null,\"endpointSourceOrder\":[\"DECLARED\",\"RUNTIME\"]}";
        
        AgentCallInterface callInterface =
            mapper.readValue(json, AgentCallInterface.class);
        assertNull(callInterface.getNativeDescriptor());
    }
    
    @Test
    void testAgentRoundTripWithAllFields() throws JsonProcessingException {
        AgentSummary restored = roundTrip(newAgent(), AgentSummary.class);
        
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
        assertVersionCatalog(restored.getVersionInfo());
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
        summary.getVersionInfo().setOnlineVersions(newVersionCatalog().getOnlineVersions());
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
        assertVersionCatalog(restored.getVersionInfo());
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
        detail.setPublishPipelineInfo(publishPipelineInfo());
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
        assertEquals(publishPipelineInfo(), restored.getPublishPipelineInfo());
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
        Endpoint item = newEndpoint("https://runtime.example.com:443/a2a", true);
        item.setBindings(Collections.singletonList(binding));
        item.setEnabled(true);
        item.setHealthy(true);
        
        RuntimeEndpointSnapshot snapshot = new RuntimeEndpointSnapshot();
        snapshot.setCallInterface(new AgentCallInterface());
        EndpointSet runtimeSet = new EndpointSet();
        runtimeSet.setSource(EndpointSource.RUNTIME);
        runtimeSet.setLastUpdatedTime(2L);
        snapshot.getCallInterface().setEndpointSets(Collections.singletonList(runtimeSet));
        snapshot.setNamespaceId("public");
        snapshot.setAgentName("Demo Agent");
        snapshot.getCallInterface().setProtocol("a2a");
        snapshot.setVersion("1.0.0");
        snapshot.getCallInterface().getEndpointSets().get(0)
            .setEndpoints(Collections.singletonList(item));
        
        com.fasterxml.jackson.databind.JsonNode json = mapper.valueToTree(snapshot);
        assertEquals("RUNTIME", json.at("/callInterface/endpointSets/0/source").asText());
        assertEquals("https://runtime.example.com:443/a2a",
            json.at("/callInterface/endpointSets/0/endpoints/0/uri").asText());
        assertFalse(json.has("items"));
        assertFalse(json.at("/callInterface/endpointSets/0/endpoints/0").has("endpoint"));
        assertFalse(json.at("/callInterface/endpointSets/0/endpoints/0").has("lastUpdatedTime"));
        RuntimeEndpointSnapshot restored = roundTrip(snapshot, RuntimeEndpointSnapshot.class);
        assertEquals("public", restored.getNamespaceId());
        assertEquals("Demo Agent", restored.getAgentName());
        assertEquals("a2a", restored.getCallInterface().getProtocol());
        assertEquals("1.0.0", restored.getVersion());
        Endpoint restoredItem =
            restored.getCallInterface().getEndpointSets().get(0).getEndpoints().get(0);
        assertEndpoint(restoredItem, true);
        assertEquals("1.0.6", restoredItem.getBindings().get(0).getRuntimeVersion());
        assertEquals("[1.0.0,2.0.0)", restoredItem.getBindings().get(0).getVersionRange());
        assertEquals(Boolean.TRUE, restoredItem.getEnabled());
        assertEquals(Boolean.TRUE, restoredItem.getHealthy());
        assertEquals(Long.valueOf(2L),
            restored.getCallInterface().getEndpointSets().get(0).getLastUpdatedTime());
    }
    
    @Test
    void testAdminRequestModelsDoNotCarryNamespace() throws JsonProcessingException {
        AgentUpdateRequest update = new AgentUpdateRequest();
        update.setAgentName("Demo Agent");
        update.setStatus(AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        update.validate();
        
        AgentVersionRequest command = new AgentVersionRequest();
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
    void testDraftValidationTreatsBlankSourceAsAbsent() {
        for (String source : Arrays.asList(null, "", " \t")) {
            AgentDraftCreateRequest request = new AgentDraftCreateRequest();
            request.setAgentName("Demo Agent");
            request.setVersion("1.0.0");
            request.setBasedOnVersion(source);
            assertThrows(IllegalArgumentException.class, request::validate);
            request.setCallInterfaces(Collections.emptyList());
            request.validate();
        }
    }
    
    @Test
    void testClientSearchQueryRoundTripWithoutNamespace() throws JsonProcessingException {
        AgentSearchRequest query = new AgentSearchRequest();
        query.setAgentNameContains("assistant");
        query.setTagsAll(Arrays.asList("search", "docs"));
        query.setProtocolsAny(Arrays.asList("a2a", "mcp"));
        query.setPageNo(2);
        query.setPageSize(10);
        
        AgentSearchRequest restored = roundTrip(query, AgentSearchRequest.class);
        assertEquals("assistant", restored.getAgentNameContains());
        assertEquals(Arrays.asList("search", "docs"), restored.getTagsAll());
        assertEquals(Arrays.asList("a2a", "mcp"), restored.getProtocolsAny());
        assertEquals(Integer.valueOf(2), restored.getPageNo());
        assertEquals(Integer.valueOf(10), restored.getPageSize());
        assertFalse(mapper.readTree(mapper.writeValueAsString(query)).has("namespaceId"));
    }
    
    @Test
    void testClientEndpointRegistrationRoundTripWithCompleteBatch() throws JsonProcessingException {
        AgentEndpointRegistrationBatch registration =
            new AgentEndpointRegistrationBatch();
        registration.setAgentName("Demo Agent");
        registration.setRuntimeVersion("1.0.6");
        registration.setVersionRange("[1.0.0,2.0.0)");
        registration.setProtocol("a2a");
        registration.setEndpoints(Arrays.asList(
            newEndpoint("https://runtime.example.com:443/a2a", true),
            newEndpoint("https://declared.example.com:443/a2a", null)));
        
        AgentEndpointRegistrationBatch restored =
            roundTrip(registration, AgentEndpointRegistrationBatch.class);
        assertEquals("Demo Agent", restored.getAgentName());
        assertEquals("1.0.6", restored.getRuntimeVersion());
        assertEquals("[1.0.0,2.0.0)", restored.getVersionRange());
        assertEquals("a2a", restored.getProtocol());
        assertEquals(2, restored.getEndpoints().size());
        assertEndpoint(restored.getEndpoints().get(0), true);
        assertEndpoint(restored.getEndpoints().get(1), null);
        assertFalse(mapper.readTree(mapper.writeValueAsString(registration)).has("namespaceId"));
    }
    
    @Test
    void testClientInputsLeaveNullPolicyToUnconfiguredMapper() throws JsonProcessingException {
        ObjectMapper plainMapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode search =
            plainMapper.valueToTree(new AgentSearchRequest());
        assertEquals(5, search.size());
        search.elements().forEachRemaining(value -> assertTrue(value.isNull()));
        com.fasterxml.jackson.databind.JsonNode batch =
            plainMapper.valueToTree(new AgentEndpointRegistrationBatch());
        assertTrue(batch.get("endpoints").isNull());
        assertFalse(batch.has("namespaceId"));
    }
    
    @Test
    void testClientInputsPreserveExplicitEmptyLists() throws JsonProcessingException {
        AgentSearchRequest query = new AgentSearchRequest();
        query.setTagsAll(Collections.emptyList());
        query.setProtocolsAny(Collections.emptyList());
        AgentEndpointRegistrationBatch registration =
            new AgentEndpointRegistrationBatch();
        registration.setEndpoints(Collections.emptyList());
        
        AgentSearchRequest restored = roundTrip(query, AgentSearchRequest.class);
        assertEquals(Collections.emptyList(), restored.getTagsAll());
        assertEquals(Collections.emptyList(), restored.getProtocolsAny());
        assertEquals(Collections.emptyList(),
            roundTrip(registration, AgentEndpointRegistrationBatch.class).getEndpoints());
    }
    
    @Test
    void testSharedBasesCannotBeConstructedAndClientInputsRejectNamespace() throws Exception {
        for (Class<?> type : Arrays.asList(AbstractAgentMetadata.class,
            AbstractAgentDraftRequest.class)) {
            assertTrue(Modifier.isAbstract(type.getModifiers()), type.getName());
            assertTrue(Modifier.isProtected(type.getDeclaredConstructor().getModifiers()),
                type.getName());
        }
        for (Class<?> type : Arrays.asList(AgentSearchRequest.class,
            AgentEndpointRegistrationBatch.class,
            AgentPublishRequest.class)) {
            assertFalse(Modifier.isAbstract(type.getModifiers()));
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("getNamespaceId"));
            assertThrows(NoSuchMethodException.class,
                () -> type.getMethod("setNamespaceId", String.class));
        }
        assertSiblingRequests(AgentPublishRequest.class, AgentDraftCreateRequest.class);
    }
    
    @Test
    void testUnifiedCallInterfaceKeepsContextSpecificJson() throws Exception {
        String common = "\"protocol\":\"a2a\",\"protocolVersion\":\"1.0.0\","
            + "\"descriptorMediaType\":\"application/json\","
            + "\"nativeDescriptor\":{\"name\":\"demo\"}";
        String definition = "{" + common + ",\"endpointSourceOrder\":[\"DECLARED\",\"RUNTIME\"],"
            + "\"endpointSets\":[{\"source\":\"DECLARED\",\"endpoints\":[{"
            + "\"uri\":\"https://example.com/a2a\",\"transport\":\"JSON-RPC\"}]}]}";
        String discovery = "{" + common + ",\"endpointSets\":[{\"source\":\"DECLARED\","
            + "\"endpoints\":[{\"uri\":\"https://example.com/a2a\","
            + "\"transport\":\"JSON-RPC\"}]}]}";
        AgentCallInterface declared =
            mapper.readValue(definition, AgentCallInterface.class);
        AgentCallInterface resolved =
            mapper.readValue(discovery, AgentCallInterface.class);
        com.fasterxml.jackson.databind.node.ObjectNode expectedDefinition =
            (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(definition);
        com.fasterxml.jackson.databind.node.ObjectNode expectedDiscovery =
            (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(discovery);
        for (com.fasterxml.jackson.databind.node.ObjectNode expected : Arrays
            .asList(expectedDefinition, expectedDiscovery)) {
            com.fasterxml.jackson.databind.node.ObjectNode endpoint =
                (com.fasterxml.jackson.databind.node.ObjectNode) expected
                    .at("/endpointSets/0/endpoints/0");
            endpoint.put("priority", 0);
            endpoint.put("weight", 1D);
            endpoint.put("healthy", true);
            endpoint.put("enabled", true);
        }
        assertEquals(expectedDefinition, mapper.valueToTree(declared));
        assertEquals(expectedDiscovery, mapper.valueToTree(resolved));
        assertFalse(mapper.valueToTree(declared).has("declaredEndpoints"));
        assertFalse(mapper.valueToTree(resolved).has("declaredEndpoints"));
        assertFalse(mapper.valueToTree(resolved).has("endpointSourceOrder"));
    }
    
    @Test
    void testConcreteSummaryJsonExcludesDetailFields() throws Exception {
        String metadata = "{\"agentName\":\"demo\",\"displayName\":\"Demo\","
            + "\"description\":\"description\",\"iconUrl\":\"https://example.com/icon\","
            + "\"provider\":{\"name\":\"Nacos\",\"url\":\"https://nacos.io\"},"
            + "\"tags\":[\"test\"],\"namespaceId\":\"public\",\"status\":\"enable\"}";
        AgentSummary summary = mapper.readValue(metadata, AgentSummary.class);
        AgentSummary detail = mapper.readValue(metadata, AgentSummary.class);
        detail.setExtensions(Collections.singletonMap("example.com/private", "value"));
        assertEquals(mapper.readTree(metadata), mapper.valueToTree(summary));
        assertFalse(mapper.valueToTree(summary).has("extensions"));
        assertTrue(mapper.valueToTree(detail).has("extensions"));
        String version = "{\"version\":\"1.0.0\",\"status\":\"online\",\"author\":\"user\"}";
        AgentVersionSummary versionSummary = mapper.readValue(version, AgentVersionSummary.class);
        AgentVersionDetail versionDetail = mapper.readValue(version, AgentVersionDetail.class);
        versionDetail.setNamespaceId("public");
        versionDetail.setAgentName("demo");
        versionDetail.setCallInterfaces(Collections.singletonList(newCallInterface()));
        assertEquals(mapper.readTree(version), mapper.valueToTree(versionSummary));
        assertFalse(mapper.valueToTree(versionSummary).has("callInterfaces"));
        assertFalse(mapper.valueToTree(versionSummary).has("namespaceId"));
        assertTrue(mapper.valueToTree(versionDetail).has("callInterfaces"));
    }
    
    @Test
    void testSharedCatalogVersionPreservesExplicitEmptyAndAbsentLabels() throws Exception {
        String management = "{\"labels\":{\"latest\":\"1.0.0\"},\"onlineVersions\":[{"
            + "\"version\":\"1.0.0\",\"labels\":[],\"protocols\":[\"a2a\"]}]}";
        String discovery =
            "{\"agentName\":\"demo\",\"versionInfo\":{\"labels\":{\"latest\":\"1.0.0\"},"
                + "\"onlineVersions\":[{\"version\":\"1.0.0\",\"protocols\":[\"a2a\"]}]}}";
        AgentVersionInfo catalog = mapper.readValue(management, AgentVersionInfo.class);
        AgentSummary entry = mapper.readValue(discovery, AgentSummary.class);
        assertEquals(AgentVersionSummary.class, catalog.getOnlineVersions().get(0).getClass());
        assertEquals(AgentVersionSummary.class,
            entry.getVersionInfo().getOnlineVersions().get(0).getClass());
        assertEquals(mapper.readTree(management), mapper.valueToTree(catalog));
        assertEquals(mapper.readTree(discovery), mapper.valueToTree(entry));
        assertEquals(Collections.emptyList(), catalog.getOnlineVersions().get(0).getLabels());
        assertNull(entry.getVersionInfo().getOnlineVersions().get(0).getLabels());
    }
    
    @Test
    void testUnifiedVersionInfoKeepsOnePublicRepresentation() throws Exception {
        AgentSummary agent = newAgent();
        AgentVersionInfo info = agent.getVersionInfo();
        info.getLabels().put("archived", "0.9.0");
        String json = mapper.writeValueAsString(agent);
        assertFalse(mapper.readTree(json).has("versionCatalog"));
        assertFalse(mapper.readTree(json).path("versionInfo").has("latestVersion"));
        assertFalse(mapper.readTree(json).path("versionInfo").has("onlineCnt"));
        AgentSummary restored = mapper.readValue(json, AgentSummary.class);
        assertEquals("0.9.0", restored.getVersionInfo().getLabels().get("archived"));
        assertEquals(info.getEditingVersion(), restored.getVersionInfo().getEditingVersion());
        assertEquals(info.getReviewingVersion(), restored.getVersionInfo().getReviewingVersion());
        assertEquals("1.0.0", restored.getVersionInfo().latestVersion());
        assertEquals(Integer.valueOf(1), restored.getVersionInfo().onlineCnt());
        restored.getVersionInfo().setOnlineVersions(Collections.emptyList());
        restored.getVersionInfo().getLabels().remove("latest");
        assertEquals(Integer.valueOf(0), restored.getVersionInfo().onlineCnt());
        assertNull(restored.getVersionInfo().latestVersion());
        assertEquals(0, new AgentVersionInfo().onlineCnt());
        assertNull(new AgentVersionInfo().latestVersion());
    }
    
    private void assertSiblingRequests(Class<?> client, Class<?> wireOrAdmin) {
        assertEquals(client.getSuperclass(), wireOrAdmin.getSuperclass());
        assertFalse(client.isAssignableFrom(wireOrAdmin));
        assertFalse(wireOrAdmin.isAssignableFrom(client));
    }
    
    private AgentSummary newAgent() {
        AgentSummary agent = new AgentSummary();
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
        agent.getVersionInfo().setOnlineVersions(newVersionCatalog().getOnlineVersions());
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
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("latest", "1.0.0");
        labels.put("stable", "1.0.0");
        versionInfo.setLabels(labels);
        return versionInfo;
    }
    
    private AgentVersionInfo newVersionCatalog() {
        AgentVersionSummary entry = new AgentVersionSummary();
        entry.setVersion("1.0.0");
        entry.setLabels(Collections.singletonList("stable"));
        entry.setProtocols(Collections.singletonList("a2a"));
        AgentVersionInfo catalog = new AgentVersionInfo();
        catalog.setLabels(new LinkedHashMap<String, String>(
            Collections.singletonMap("latest", "1.0.0")));
        catalog.setOnlineVersions(Collections.singletonList(entry));
        return catalog;
    }
    
    private AgentVersionSummary newVersionSummary() {
        AgentVersionSummary summary = new AgentVersionSummary();
        summary.setVersion("1.0.0");
        summary.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        summary.setPublishPipelineInfo(publishPipelineInfo());
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
        
        EndpointSet declaredSet1 = new EndpointSet();
        declaredSet1.setSource(EndpointSource.DECLARED);
        declaredSet1.setEndpoints(Collections.singletonList(
            newEndpoint("https://declared.example.com:443/a2a", null)));
        callInterface.setEndpointSets(Collections.singletonList(declaredSet1));
        return callInterface;
    }
    
    private Endpoint newEndpoint(String uri, Boolean healthy) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport("JSON-RPC");
        endpoint.setPriority(1);
        endpoint.setWeight(2.5D);
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        if (healthy != null) {
            endpoint.setHealthy(healthy);
        }
        return endpoint;
    }
    
    private void assertVersionInfo(AgentVersionInfo versionInfo) {
        assertEquals("2.0.0", versionInfo.getEditingVersion());
        assertEquals("2.1.0", versionInfo.getReviewingVersion());
        assertEquals(Integer.valueOf(1), versionInfo.onlineCnt());
        assertEquals("1.0.0", versionInfo.getLabels().get("latest"));
        assertEquals("1.0.0", versionInfo.getLabels().get("stable"));
    }
    
    private void assertVersionCatalog(AgentVersionInfo catalog) {
        assertEquals("1.0.0", catalog.latestVersion());
        AgentVersionSummary entry = catalog.getOnlineVersions().get(0);
        assertEquals("1.0.0", entry.getVersion());
        assertEquals(Collections.singletonList("stable"), entry.getLabels());
        assertEquals(Collections.singletonList("a2a"), entry.getProtocols());
    }
    
    private void assertVersionSummary(AgentVersionSummary summary) {
        assertEquals("1.0.0", summary.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, summary.getStatus());
        assertEquals(publishPipelineInfo(), summary.getPublishPipelineInfo());
        assertEquals("nacos", summary.getAuthor());
        assertEquals("Initial online version", summary.getChangeDescription());
        assertEquals(contentDigest(), summary.getContentDigest());
        assertEquals(Long.valueOf(1L), summary.getCreateTime());
        assertEquals(Long.valueOf(2L), summary.getUpdateTime());
    }
    
    private String publishPipelineInfo() {
        return "{\"executionId\":\"pipeline-1\",\"status\":\"REJECTED\",\"pipeline\":[]}";
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
        assertEndpoint(callInterface.getEndpointSets().get(0).getEndpoints().get(0), null);
    }
    
    private void assertEndpoint(Endpoint endpoint, Boolean healthy) {
        assertEquals(healthy == null ? "https://declared.example.com:443/a2a"
            : "https://runtime.example.com:443/a2a", endpoint.getUri());
        assertEquals("JSON-RPC", endpoint.getTransport());
        assertEquals(Integer.valueOf(1), endpoint.getPriority());
        assertEquals(Double.valueOf(2.5D), endpoint.getWeight());
        assertEquals("cn-hangzhou-a", endpoint.getMetadata().get("zone"));
        assertEquals(healthy == null || healthy, endpoint.getHealthy());
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
