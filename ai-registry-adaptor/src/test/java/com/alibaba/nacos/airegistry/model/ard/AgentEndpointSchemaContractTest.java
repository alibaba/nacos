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

package com.alibaba.nacos.airegistry.model.ard;

import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.service.agent.AgentArtifactBuilder;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates actual Agent payloads against local shared schemas without network resolution.
 *
 * @author Nacos
 */
class AgentEndpointSchemaContractTest {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final String MANAGEMENT =
        "https://nacos.io/schemas/ai/agent/agent-management.schema.json";
    
    private static final String RAD =
        "https://nacos.io/schemas/ai/rad/rad-protocol.schema.json";
    
    private static final String WATCH =
        "https://nacos.io/schemas/ai/rad/watch/rad-watch-binding.schema.json";
    
    private static final String STORAGE =
        "https://nacos.io/schemas/ai/agent/internal/v1/agent-storage.schema.json";
    
    private static final String ARTIFACT =
        "https://nacos.io/schemas/ai/agent/agent-artifact.schema.json";
    
    private static final String DEFINITION = "{\"protocol\":\"custom\","
        + "\"descriptorMediaType\":\"application/json\",\"nativeDescriptor\":{\"method\":\"invoke\"},"
        + "\"endpointSourceOrder\":[\"RUNTIME\",\"DECLARED\"],\"endpointSets\":[{\"source\":\"DECLARED\","
        + "\"endpoints\":[{\"uri\":\"https://example.com:443/rpc\",\"transport\":\"HTTP\","
        + "\"priority\":0,\"weight\":1.0}]}]}";
    
    private static SchemaRegistry registry;
    
    @BeforeAll
    static void loadSchemas() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("specs/schemas/ai"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("Cannot find repository schemas");
        }
        Map<String, String> schemas = new LinkedHashMap<>();
        for (String uri : new String[] {MANAGEMENT, RAD, STORAGE, ARTIFACT, WATCH}) {
            String relative = uri.substring("https://nacos.io/schemas/".length());
            schemas.put(uri, Files.readString(root.resolve("specs/schemas").resolve(relative)));
        }
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemas(schemas));
    }
    
    @Test
    void shouldValidateDefinitionStorageAndArtifactAndRejectRuntimeContamination()
        throws Exception {
        JsonNode definition = MAPPER.readTree(DEFINITION);
        assertValid(MANAGEMENT, "AgentCallInterface", definition);
        AgentCallInterface callInterface = MAPPER.treeToValue(definition, AgentCallInterface.class);
        AgentVersionContent content =
            new AgentVersionContent(Collections.singletonList(callInterface));
        byte[] bytes = AgentVersionContentSerializer.serialize(content).getBytes();
        assertValid(STORAGE, "AgentVersionContent", MAPPER.readTree(bytes));
        assertEquals(definition, MAPPER.readTree(bytes).at("/callInterfaces/0"));
        AgentVersionDetail detail = new AgentVersionDetail();
        detail.setAgentName("demo");
        detail.setVersion("1.0.0");
        detail.setContentDigest(AgentVersionContentSerializer.digest(bytes));
        detail.setCallInterfaces(Collections.singletonList(callInterface));
        JsonNode artifact =
            MAPPER.valueToTree(AgentArtifactBuilder.buildNacosAgentArtifact(detail));
        assertValid(ARTIFACT, "NacosAgentArtifact", artifact);
        assertEquals(MAPPER.valueToTree(callInterface), artifact.at("/callInterfaces/0"));
        assertTrue(artifact.at("/callInterfaces/0/endpointSets/0/endpoints/0/healthy").asBoolean());
        assertTrue(artifact.at("/callInterfaces/0/endpointSets/0/endpoints/0/enabled").asBoolean());
        assertTrue(artifact.at("/callInterfaces/0/protocolVersion").isNull());
        ObjectNode invalid = definition.deepCopy();
        ((ObjectNode) invalid.at("/endpointSets/0")).put("source", "RUNTIME");
        assertInvalid(MANAGEMENT, "AgentCallInterface", invalid);
        ObjectNode stored = (ObjectNode) MAPPER.readTree(bytes);
        ((ObjectNode) stored.at("/callInterfaces/0/endpointSets/0/endpoints/0")).put("healthy",
            false);
        assertInvalid(STORAGE, "AgentVersionContent", stored);
    }
    
    @Test
    void shouldValidateRuntimeViewsAndSharedEndpointStatus()
        throws Exception {
        ObjectNode endpoint =
            (ObjectNode) MAPPER.readTree("{\"uri\":\"https://example.com:443/rpc\","
                + "\"transport\":\"HTTP\",\"priority\":0,\"weight\":1.0,\"healthy\":false,"
                + "\"enabled\":true,\"state\":\"UNHEALTHY\","
                + "\"bindings\":[{\"runtimeVersion\":\"1.0.0\",\"versionRange\":\"[1.0.0]\"}]}");
        ObjectNode set = MAPPER.createObjectNode();
        set.put("source", "RUNTIME");
        set.put("lastUpdatedTime", 123L);
        set.putArray("endpoints").add(endpoint);
        ObjectNode callInterface = MAPPER.createObjectNode();
        callInterface.put("protocol", "custom");
        callInterface.putArray("endpointSets").add(set);
        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("namespaceId", "public");
        snapshot.put("agentName", "demo");
        snapshot.set("callInterface", callInterface);
        assertValid(MANAGEMENT, "RuntimeEndpointSnapshot", snapshot);
        com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot typed = MAPPER.treeToValue(
            snapshot, com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot.class);
        assertValid(MANAGEMENT, "RuntimeEndpointSnapshot", MAPPER.valueToTree(typed));
        assertInvalid(RAD, "AgentCallInterface", callInterface);
        callInterface.put("descriptorMediaType", "application/json");
        callInterface.set("nativeDescriptor", MAPPER.createObjectNode());
        set.remove("lastUpdatedTime");
        set.put("sourceRevision", "murmur3-x64-128-v1:0123456789abcdef0123456789abcdef");
        endpoint.remove("enabled");
        endpoint.remove("state");
        assertValid(RAD, "AgentCallInterface", callInterface);
        endpoint.put("state", "UNHEALTHY");
        assertValid(RAD, "AgentCallInterface", callInterface);
    }
    
    @Test
    void shouldAcceptPublicationHealthAndSharedDeregistrationFields()
        throws Exception {
        ObjectNode endpoint = (ObjectNode) MAPPER.readTree("{\"uri\":\"https://example.com/rpc\","
            + "\"transport\":\"HTTP\",\"healthy\":false,\"enabled\":false,\"state\":\"DISABLED\","
            + "\"bindings\":[{\"runtimeVersion\":\"9.0.0\",\"versionRange\":\"ignored\"}]}");
        assertValid(RAD, "PublicationEndpoint", endpoint);
        assertValid(RAD, "EndpointKey", endpoint);
        endpoint.remove("healthy");
        assertValid(RAD, "EndpointKey", endpoint);
        endpoint.put("healthy", "invalid");
        assertInvalid(RAD, "PublicationEndpoint", endpoint);
    }
    
    @Test
    void shouldValidateActualMapperNullOutputAndRejectInvalidEndpointScalars() throws Exception {
        AgentCallInterface definition = MAPPER.readValue(DEFINITION, AgentCallInterface.class);
        assertValid(MANAGEMENT, "AgentCallInterface", MAPPER.valueToTree(definition));
        assertValid(MANAGEMENT, "AgentCallInterface",
            MAPPER.readTree(JsonUtils.toJson(definition)));
        Endpoint endpoint = definition.getEndpointSets().get(0).getEndpoints().get(0);
        ObjectNode json = MAPPER.valueToTree(endpoint);
        assertTrue(json.get("bindings").isNull());
        assertTrue(json.get("state").isNull());
        assertValid(RAD, "DeclaredEndpoint", json);
        com.alibaba.nacos.api.ai.model.agent.AgentSummary summary =
            new com.alibaba.nacos.api.ai.model.agent.AgentSummary();
        summary.setAgentName("demo");
        com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo info =
            new com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo();
        info.setLabels(Collections.singletonMap("latest", "1.0.0"));
        com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary version =
            new com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary();
        version.setVersion("1.0.0");
        version.setProtocols(Collections.singletonList("custom"));
        info.setOnlineVersions(Collections.singletonList(version));
        summary.setVersionInfo(info);
        assertValid(RAD, "AgentSummary", MAPPER.valueToTree(summary));
        for (String field : new String[] {"priority", "weight", "healthy", "enabled"}) {
            ObjectNode invalid = json.deepCopy();
            invalid.putNull(field);
            assertInvalid(RAD, "DeclaredEndpoint", invalid);
            invalid.put(field, "invalid");
            assertInvalid(MANAGEMENT, "DeclaredEndpoint", invalid);
        }
        ObjectNode invalid = json.deepCopy();
        invalid.put("priority", -1);
        assertInvalid(RAD, "DeclaredEndpoint", invalid);
        invalid.put("priority", 2147483648L);
        assertInvalid(RAD, "DeclaredEndpoint", invalid);
        invalid = json.deepCopy();
        invalid.put("weight", 10001);
        assertInvalid(RAD, "DeclaredEndpoint", invalid);
        invalid = json.deepCopy();
        invalid.put("unexpected", true);
        assertInvalid(RAD, "DeclaredEndpoint", invalid);
    }
    
    @Test
    void shouldTreatAbsentAndNullSelectorsAsEquivalentWithoutAllowingBoth() throws Exception {
        com.alibaba.nacos.api.ai.model.agent.AgentReference reference =
            new com.alibaba.nacos.api.ai.model.agent.AgentReference();
        reference.setAgentName("demo");
        assertValid(RAD, "AgentReference", MAPPER.valueToTree(reference));
        reference.setVersion("1.0.0");
        assertValid(RAD, "AgentReference", MAPPER.valueToTree(reference));
        reference.setLabel("latest");
        assertInvalid(RAD, "AgentReference", MAPPER.valueToTree(reference));
        reference.setVersion(null);
        assertValid(RAD, "AgentReference", MAPPER.valueToTree(reference));
        com.alibaba.nacos.api.ai.model.agent.AgentWatchBatchResponse response =
            new com.alibaba.nacos.api.ai.model.agent.AgentWatchBatchResponse();
        response.setGeneration(1L);
        assertValid(WATCH, "AgentWatchBatchResponse", MAPPER.valueToTree(response));
        response.setChanged(true);
        assertInvalid(WATCH, "AgentWatchBatchResponse", MAPPER.valueToTree(response));
        response.setChangedClientWatchIds(Collections.singletonList("watch-1"));
        assertValid(WATCH, "AgentWatchBatchResponse", MAPPER.valueToTree(response));
    }
    
    @Test
    void shouldKeepStoredDescriptorAndDiscoveryFingerprintStableAcrossJsonRoundTrips()
        throws Exception {
        AgentCallInterface definition = MAPPER.readValue(DEFINITION, AgentCallInterface.class);
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("method", "invoke");
        descriptor.put("absent", null);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("value", "retained");
        nested.put("absent", null);
        descriptor.put("nested", nested);
        definition.setNativeDescriptor(descriptor);
        AgentVersionContentSerializer.SerializedContent stored =
            AgentVersionContentSerializer.serialize(
                new AgentVersionContent(Collections.singletonList(definition)));
        AgentCallInterface readBack = AgentVersionContentSerializer.deserialize(stored.getBytes())
            .getCallInterfaces().get(0);
        // Existing storage policy filters null object members; wire serialization must be stable thereafter.
        assertFalse(MAPPER.readTree(stored.getBytes()).at("/callInterfaces/0/nativeDescriptor")
            .has("absent"));
        assertEquals("retained", MAPPER.readTree(stored.getBytes())
            .at("/callInterfaces/0/nativeDescriptor/nested/value").asText());
        readBack.setEndpointSourceOrder(null);
        readBack.getEndpointSets().get(0).setSourceRevision(stored.getContentDigest());
        com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult result =
            new com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("demo");
        result.setVersion("1.0.0");
        result.setContentDigest(stored.getContentDigest());
        result.setCallInterfaces(Collections.singletonList(readBack));
        String fingerprint =
            com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer.fingerprint(result);
        for (String json : new String[] {MAPPER.writeValueAsString(result),
            JsonUtils.toJson(result)}) {
            assertValid(RAD, "AgentDiscoveryResult", MAPPER.readTree(json));
            com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult restored =
                JsonUtils.toObj(json,
                    com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult.class);
            assertEquals(fingerprint,
                com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer.fingerprint(restored));
        }
    }
    
    private void assertValid(String document, String entrypoint, JsonNode instance) {
        var errors = registry.getSchema(SchemaLocation.of(document + "#/$defs/" + entrypoint))
            .validate(instance);
        assertTrue(errors.isEmpty(), errors.toString());
    }
    
    @Test
    void shouldComposeSearchBusinessFieldsWithRequiredNamespaceContext() throws Exception {
        AgentSearchRequest search = new AgentSearchRequest();
        search.setAgentNameContains("schema-agent");
        search.setTagsAll(Collections.singletonList("team"));
        search.setProtocolsAny(Collections.singletonList("a2a"));
        ObjectNode business = (ObjectNode) MAPPER.readTree(JsonUtils.toJson(search));
        assertFalse(business.has("namespaceId"));
        assertInvalid(RAD, "AgentSearchRequest", business);
        ObjectNode logical = business.deepCopy();
        logical.put("namespaceId", "tenant-b");
        assertValid(RAD, "AgentSearchRequest", logical);
        logical.put("namespaceId", "bad namespace");
        assertInvalid(RAD, "AgentSearchRequest", logical);
        logical.put("namespaceId", "public");
        logical.put("pageNo", 0);
        assertInvalid(RAD, "AgentSearchRequest", logical);
        assertEquals(business, MAPPER.readTree(JsonUtils.toJson(search)));
    }
    
    @Test
    void shouldComposeRegistrationBusinessFieldsWithRequiredNamespaceContext() throws Exception {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://example.com:443/rpc");
        endpoint.setTransport("HTTP");
        endpoint.setHealthy(false);
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setAgentName("schema-agent");
        batch.setProtocol("a2a");
        batch.setRuntimeVersion("1.0.0");
        batch.setVersionRange("[1.0.0,2.0.0)");
        batch.setEndpoints(Collections.singletonList(endpoint));
        ObjectNode business = (ObjectNode) MAPPER.readTree(JsonUtils.toJson(batch));
        assertFalse(business.has("namespaceId"));
        assertInvalid(RAD, "AgentEndpointRegistrationBatch", business);
        ObjectNode logical = business.deepCopy();
        logical.put("namespaceId", "tenant-b");
        assertValid(RAD, "AgentEndpointRegistrationBatch", logical);
        logical.put("namespaceId", "bad namespace");
        assertInvalid(RAD, "AgentEndpointRegistrationBatch", logical);
        logical.put("namespaceId", "public");
        logical.set("endpoints", MAPPER.createArrayNode());
        assertInvalid(RAD, "AgentEndpointRegistrationBatch", logical);
        assertEquals(business, MAPPER.readTree(JsonUtils.toJson(batch)));
    }
    
    private void assertInvalid(String document, String entrypoint, JsonNode instance) {
        assertFalse(
            registry.getSchema(SchemaLocation.of(document + "#/$defs/" + entrypoint))
                .validate(instance).isEmpty());
    }
}
