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

import com.alibaba.nacos.airegistry.contract.ArdContractSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract assertions derived from the pinned upstream ARD OpenAPI.
 *
 * @author nacos
 */
class ArdOpenApiContractTest {
    
    private static final String OPEN_API =
        "/ard-spec/5fa2f5aef790b478319f6a3b43adf4661b0ed0e0/ard.openapi.yaml";
    
    private static Map<String, Object> document;
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final ArdContractSchemas CONTRACTS = new ArdContractSchemas();
    
    @Test
    void shouldRejectMissingModifiedAndUnpinnedFixtures() {
        assertThrows(IllegalStateException.class, () -> new ArdContractSchemas(path -> null));
        assertThrows(IllegalStateException.class,
            () -> new ArdContractSchemas(path -> new java.io.ByteArrayInputStream(
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(RuntimeException.class,
            () -> CONTRACTS.schema(ArdContractSchemas.CURRENT, "missing.schema.json", ""));
        assertEquals("ard:mediaType", loadCurrentContext().path("@context").path("type").asText());
    }
    
    @Test
    void shouldValidateCompleteEntriesAndEmptyManifests() {
        ObjectNode entry = completeEntry();
        assertTrue(CONTRACTS.validateEntry("ArdEntry", entry).isEmpty());
        for (String field : List.of("identifier", "displayName", "type", "url")) {
            ObjectNode invalid = entry.deepCopy();
            invalid.remove(field);
            assertFalse(CONTRACTS.validateEntry("ArdEntry", invalid).isEmpty(), field);
        }
        entry.set("data", MAPPER.createObjectNode());
        assertFalse(CONTRACTS.validateEntry("ArdEntry", entry).isEmpty());
        entry.remove("url");
        assertTrue(CONTRACTS.validateEntry("ArdEntry", entry).isEmpty());
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.putArray("entries");
        assertTrue(CONTRACTS.validateEntry("ArdManifest", manifest).isEmpty());
        manifest.remove("entries");
        assertFalse(CONTRACTS.validateEntry("ArdManifest", manifest).isEmpty());
    }
    
    @Test
    void shouldValidatePartialResultsAndSharedResponseIntersection() {
        ObjectNode item = completeEntry();
        ObjectNode response = MAPPER.createObjectNode();
        response.putArray("items").add(item);
        assertValidBoth("ListResponse", response);
        item.remove(List.of("displayName", "type", "url"));
        assertTrue(CONTRACTS.validateResponse(ArdContractSchemas.CURRENT, "ListResponse", response)
            .isEmpty());
        assertFalse(CONTRACTS.validateResponse(ArdContractSchemas.LEGACY, "ListResponse", response)
            .isEmpty());
        item.remove("identifier");
        assertFalse(CONTRACTS.validateResponse(ArdContractSchemas.CURRENT, "ListResponse", response)
            .isEmpty());
        
        item = completeEntry().put("score", 80).put("source",
            "https://registry.example.com/v3/ai/ard");
        response = MAPPER.createObjectNode();
        response.putArray("results").add(item);
        assertValidBoth("SearchResponse", response);
        item.remove(List.of("displayName", "type", "url"));
        assertTrue(CONTRACTS
            .validateResponse(ArdContractSchemas.CURRENT, "SearchResponse", response).isEmpty());
        assertFalse(CONTRACTS
            .validateResponse(ArdContractSchemas.LEGACY, "SearchResponse", response).isEmpty());
        for (String field : List.of("identifier", "score", "source")) {
            ObjectNode invalid = item.deepCopy();
            invalid.remove(field);
            assertFalse(CONTRACTS.schema(ArdContractSchemas.CURRENT, "ard.openapi.yaml",
                "/components/schemas/SearchResultItem").validate(invalid).isEmpty(), field);
        }
        for (double score : new double[] {-1, 101, 1.5}) {
            item.put("score", score);
            assertFalse(
                CONTRACTS.validateResponse(ArdContractSchemas.CURRENT, "SearchResponse", response)
                    .isEmpty());
        }
        item.put("score", 80).put("source", "/relative");
        assertFalse(CONTRACTS
            .validateResponse(ArdContractSchemas.CURRENT, "SearchResponse", response).isEmpty());
    }
    
    @Test
    void shouldValidateExtensionsAndLowercaseTrustSeparately() {
        ObjectNode entry = completeEntry();
        entry.putObject("@context").put("custom", "https://example.com/custom#");
        entry.put("custom:field", "value");
        entry.putObject("trustManifest").put("customProof", "framework-owned");
        assertTrue(CONTRACTS.validateEntry("ArdEntry", entry).isEmpty(),
            "Unmodified upstream property is uppercase");
        assertFalse(CONTRACTS.validateLowercaseTrust(entry).isEmpty());
        ((ObjectNode) entry.get("trustManifest")).put("identity", "https://example.com/workload");
        assertTrue(CONTRACTS.validateLowercaseTrust(entry).isEmpty());
        entry.remove("trustManifest");
        assertTrue(CONTRACTS.validateLowercaseTrust(entry).isEmpty());
    }
    
    @Test
    void shouldAcceptFrameworkDefinedTrustWithoutClaimingVerification() throws Exception {
        ObjectNode entry = completeEntry();
        var trust = MAPPER.readTree("{\"identity\":\"did:custom:subject\","
            + "\"identityType\":\"custom-pki\",\"customProof\":{\"nested\":[1,true]},"
            + "\"trustSchema\":{\"customPolicy\":true},"
            + "\"attestations\":[{\"type\":\"scan\",\"uri\":\"https://example.com/proof\","
            + "\"customScope\":\"artifact\"}],"
            + "\"provenance\":[{\"relation\":\"customRelation\",\"sourceId\":\"source\","
            + "\"frameworkData\":{}}]}");
        entry.set("trustManifest", trust);
        assertTrue(CONTRACTS.validateLowercaseTrust(entry).isEmpty());
        assertFalse(CONTRACTS.schema(ArdContractSchemas.LEGACY, "ai-catalog.schema.json",
            "/$defs/trustManifest").validate(trust).isEmpty());
        for (String invalid : List.of("null", "[]", "true", "\"identity\"", "{}",
            "{\"identity\":null}", "{\"identity\":1}")) {
            entry.set("trustManifest", MAPPER.readTree(invalid));
            assertFalse(CONTRACTS.validateLowercaseTrust(entry).isEmpty(), invalid);
        }
    }
    
    private static ObjectNode completeEntry() {
        return MAPPER.createObjectNode().put("identifier", "urn:air:example.com:skill:demo")
            .put("displayName", "Demo").put("type", "application/agent-skills+zip")
            .put("url", "https://example.com/skill.zip");
    }
    
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 0, 1, 2, 5, 6})
    void shouldTreatRepresentativeQueryCountsAsCurrentRecommendations(int count) {
        ObjectNode entry = completeEntry();
        if (count >= 0) {
            var queries = entry.putArray("representativeQueries");
            for (int i = 0; i < count; i++) {
                queries.add("Find capability " + i);
            }
        }
        assertTrue(CONTRACTS.validateEntry("ArdEntry", entry).isEmpty());
        boolean legacyValid = CONTRACTS.schema(ArdContractSchemas.LEGACY, "ai-catalog.schema.json",
            "/$defs/catalogEntry").validate(entry).isEmpty();
        assertEquals(count == -1 || count >= 2 && count <= 5, legacyValid);
    }
    
    @Test
    void shouldRejectNonStringRepresentativeQueriesInBothSchemas() {
        ObjectNode entry = completeEntry();
        entry.putArray("representativeQueries").add("Find a tool").add(1);
        assertFalse(CONTRACTS.validateEntry("ArdEntry", entry).isEmpty());
        assertFalse(CONTRACTS.schema(ArdContractSchemas.LEGACY, "ai-catalog.schema.json",
            "/$defs/catalogEntry").validate(entry).isEmpty());
    }
    
    private void assertValidBoth(String definition, ObjectNode response) {
        for (String version : List.of(ArdContractSchemas.LEGACY, ArdContractSchemas.CURRENT)) {
            var errors = CONTRACTS.validateResponse(version, definition, response);
            assertTrue(errors.isEmpty(), errors.toString());
        }
    }
    
    private static com.fasterxml.jackson.databind.JsonNode loadCurrentContext() {
        try (InputStream input = ArdOpenApiContractTest.class.getResourceAsStream(
            "/ard-spec/" + ArdContractSchemas.CURRENT + "/ard.context.jsonld")) {
            return MAPPER.readTree(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    @BeforeAll
    static void loadOpenApi() {
        try (InputStream input = ArdOpenApiContractTest.class.getResourceAsStream(OPEN_API)) {
            assertNotNull(input);
            document = new Yaml().load(input);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load pinned ARD OpenAPI", e);
        }
    }
    
    @Test
    void shouldPinExpectedUpstreamVersionAndUnauthorizedContract() {
        assertEquals("0.5.0", map(document.get("info")).get("version"));
        Map<String, Object> responses = operation("/search", "post", "responses");
        assertEquals("#/components/responses/401Unauthorized",
            map(responses.get("401")).get("$ref"));
        
        Map<String, Object> error = schema("Error");
        assertEquals(List.of("errorCode", "message"), error.get("required"));
    }
    
    @Test
    void shouldPinSearchListAndExploreResponseShapes() {
        Map<String, Object> search = schema("SearchResponse");
        assertTrue(list(search.get("required")).contains("results"));
        Map<String, Object> searchItem = schema("SearchResultItem");
        assertTrue(list(searchItem.get("required")).containsAll(List.of("score", "source")));
        Map<String, Object> searchProperties = map(searchItem.get("properties"));
        assertEquals("integer", map(searchProperties.get("score")).get("type"));
        assertEquals("uri", map(searchProperties.get("source")).get("format"));
        
        Map<String, Object> list = schema("ListResponse");
        assertTrue(list(list.get("required")).contains("items"));
        
        Map<String, Object> explore = schema("ExploreResponse");
        assertTrue(list(explore.get("required")).containsAll(List.of("resultType", "facets")));
    }
    
    private Map<String, Object> operation(String path, String method, String field) {
        Map<String, Object> paths = map(document.get("paths"));
        Map<String, Object> operation = map(map(paths.get(path)).get(method));
        return map(operation.get(field));
    }
    
    private Map<String, Object> schema(String name) {
        Map<String, Object> components = map(document.get("components"));
        return map(map(components.get("schemas")).get(name));
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
    
    @SuppressWarnings("unchecked")
    private static List<String> list(Object value) {
        return (List<String>) value;
    }
}
