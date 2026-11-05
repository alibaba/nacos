/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.openapi.ard;

import com.alibaba.nacos.airegistry.contract.ArdContractSchemas;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Live standalone-server conformance tests for the independent ARD web adaptor.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: Admin APIs publish Agent, Skill, Prompt, and MCP resources through
 *     the main server, while the separate ARD web context recalls the same durable shared-index
 *     projections through Search, List, Explore, Catalog, and exact artifact URLs; MCP uses
 *     its canonical name while retaining the compatible ID in metadata.</li>
 *     <li>Representation boundary: a pure A2A Agent defaults to an A2A Agent Card, a
 *     multi-protocol Agent defaults to the Nacos Agent artifact while remaining selectable as
 *     A2A, and an Agent whose latest Version is custom-only is excluded from A2A results even
 *     when an older online Version supported A2A.</li>
 *     <li>Exception/error handling: namespace conflicts use the exact external ARD error shape
 *     without the Nacos {@code Result<T>} envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ArdAdaptorOpenApiITCase extends AiAdminApiBaseITCase {
    
    private static final String ARD_PORT =
            System.getProperty("nacos.ai.registry.port", "9080");
    
    private static final String ARD_BASE_URL =
            "http://" + NACOS_HOST + ':' + ARD_PORT;
    
    private static final String ARD_PATH = "/v3/ai/ard";
    
    private static final String TYPE_A2A = "application/a2a-agent-card+json";
    
    private static final String TYPE_NACOS_AGENT = "application/vnd.nacos.ai-agent+json";
    
    private static final String TYPE_SKILL = "application/agent-skills+zip";
    
    private static final String TYPE_PROMPT = "application/vnd.nacos.ai-prompt+json";
    
    private static final String TYPE_MCP = "application/mcp-server-card+json";
    
    private static final int SEARCH_MAX_RETRIES = 120;
    
    private static final long SEARCH_RETRY_INTERVAL_MILLIS = 250L;

    private static final ArdContractSchemas CONTRACTS = new ArdContractSchemas();

    @Test
    public void testQueryContextAndIndependentFilterGroups() throws Exception {
        String marker = randomAiName("ard-query-groups");
        String ab = marker + "-ab";
        String a = marker + "-a";
        String b = marker + "-b";
        publishTaggedAgent(ab, marker, List.of("a", "b", "1"));
        publishTaggedAgent(a, marker, List.of("a"));
        publishTaggedAgent(b, marker, List.of("b"));
        awaitSearch(searchRequest(marker, TYPE_A2A), Set.of(ab, a, b));
        assertQueryNames(marker, null, Map.of("tags", List.of("a", "b")), null, Set.of(ab, a, b));
        String ns = "https://agenticresourcediscovery.org/ns#";
        assertQueryNames(marker, Map.of("t", ns + "tags"),
            Map.of("tags", List.of("a"), "t", List.of("b")), null, Set.of(ab));
        for (Object context : List.of("https://agenticresourcediscovery.org/context/v1",
            Map.of("a", ns), List.of(Map.of("a", ns), Map.of("b", ns)))) {
            assertQueryNames(marker, context, Map.of(ns + "mediaType", TYPE_A2A), null, Set.of(ab, a, b));
        }
        assertQueryNames(marker, Map.of("t", ns + "mediaType"),
            Map.of("type", TYPE_A2A, "t", TYPE_SKILL), null, Set.of());
        assertQueryNames(marker, Arrays.asList(Map.of("tags", "https://other.example/tags"), null),
            Map.of("tags", "a"), null, Set.of(ab, a));
        assertQueryNames(marker, Collections.singletonMap("t", null),
            Map.of("t", "a"), null, Set.of());
        assertQueryNames(marker, Map.of("m", ns + "metadata"),
            Map.of("m.resourceName", ab), null, Set.of(ab));
        assertQueryNames(marker, Map.of("tags", "https://other.example/tags"),
            Map.of("tags", "a"), null, Set.of());
        assertQueryNames(marker, null, Map.of("tags", "a"),
            List.of(Map.of("fieldPath", "tags", "values", List.of("b"))), Set.of(ab, a, b));
        assertQueryNames(marker, Map.of("t", ns + "tags"), Map.of("t", "a"),
            List.of(Map.of("fieldPath", "tags", "values", List.of("b"))), Set.of(ab));
        assertQueryNames(marker, null, Map.of("tags", List.of()), null, Set.of(ab, a, b));
        assertQueryNames(marker, null, Map.of("type", List.of()), null, Set.of());
        assertQueryNames(marker, null, Map.of("tags", "A"), null, Set.of(ab, a));
        assertQueryNames(marker, null, Map.of(ns + "Tags", "a"), null, Set.of());
        assertQueryNames(marker, null, Map.of("unknown.path", "a"), null, Set.of());
        assertQueryNames(marker, null, Map.of("tags", "1"), null, Set.of(ab));
        assertQueryNames(marker, null, Map.of(),
            List.of(Map.of("fieldPath", "tags", "values", List.of(1))), Set.of(ab));
    }

    @Test
    public void testQueryValidationKeepsLegacyHttpBinding() throws Exception {
        for (String value : List.of("1", "true", "{}", "null", "[1]", "[true]", "[{}]", "[null]", "[[]]")) {
            assertArdInvalid(ardRawJson("{\"query\":{\"text\":\"validation\",\"filter\":{\"tags\":" + value + "}}}"));
        }
        for (String context : List.of("null", "123", "true", "[123]", "{\"a\":{\"@id\":123}}",
            "\"https://denied.example/context\"")) {
            assertArdInvalid(ardRawJson("{\"query\":{\"text\":\"validation\",\"@context\":" + context + "}}"));
        }
        for (String body : List.of("{}", "{", "{\"query\":{}}", "{\"query\":{\"text\":\" \"}}",
            "{\"query\":{\"text\":\"validation\"},\"federation\":\"AUTO\"}")) {
            assertArdInvalid(ardRawJson(body));
        }
        for (String text : List.of("123", "true", "\"validation\"")) {
            assertEquals(200, ardRawJson("{\"query\":{\"text\":" + text + "}}").code());
        }
        assertEquals(200, ardRawJson("{\"unknown\":1,\"query\":{\"text\":\"\",\"text\":\"validation\",\"unknown\":2}}").code());
        assertArdInvalid(ardRawJson("{\"query\":{\"text\":\"validation\",\"text\":\"\"}}"));
    }

    @Test
    public void testQueryCursorContinuationAndExplicitRecovery() throws Exception {
        String marker = randomAiName("ard-cursor");
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String name = marker + "-" + i;
            publishPublicAgent(name, "1.0.0");
            expected.add(name);
        }
        Map<String, Object> request = searchRequest(marker, TYPE_A2A);
        for (String name : expected) {
            awaitSearch(searchRequest(name, TYPE_A2A), Set.of(name));
        }
        request.put("pageSize", 1);
        JsonNode first = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, request);
        String token = first.path("pageToken").asText();
        assertFalse(token.isBlank(), first.toString());
        request.put("pageToken", token);
        request.put("pageSize", 2);
        request.put("query", Map.of("text", marker,
            "@context", Map.of("a", "https://agenticresourcediscovery.org/ns#"),
            "filter", Map.of("a:mediaType", TYPE_A2A)));
        Set<String> names = resourceNames(first.path("results"));
        for (int page = 0; page < 4; page++) {
            JsonNode next = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, request);
            Set<String> following = resourceNames(next.path("results"));
            assertTrue(Collections.disjoint(names, following));
            names.addAll(following);
            assertResponseSchemas("SearchResponse", next);
            if (!next.hasNonNull("pageToken")) {
                break;
            }
            request.put("pageToken", next.path("pageToken").asText());
        }
        assertEquals(expected, names);

        request.put("pageToken", "not-a-valid-page-token");
        assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/search", DEFAULT_NAMESPACE, request));
        // Recovery is a new query with the original conditions and discarded prior results.
        request.remove("pageToken");
        request.put("pageSize", 10);
        Set<String> recovered = new HashSet<>();
        for (int page = 0; page < 4; page++) {
            JsonNode response = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, request);
            Set<String> current = resourceNames(response.path("results"));
            assertTrue(Collections.disjoint(recovered, current));
            recovered.addAll(current);
            if (!response.hasNonNull("pageToken")) {
                break;
            }
            request.put("pageToken", response.path("pageToken").asText());
        }
        assertEquals(expected, recovered);
        request.remove("pageToken");
        request.put("query", Map.of("text", ""));
        assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/search", DEFAULT_NAMESPACE, request));
        request.put("pageToken", token);
        assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/search", DEFAULT_NAMESPACE, request));
    }

    @Test
    public void testQueryNamespaceBindingAndContextIsolation() throws Exception {
        String name = randomAiName("ard-namespace");
        String namespace = randomAiName("ard-ns");
        String namespacePath = nacosPath("/v3/admin/core/namespace");
        postFormOk(namespacePath, Query.newInstance().addParam("namespaceId", namespace)
            .addParam("namespaceName", namespace));
        addCleanup(() -> deleteQuietly(namespacePath, Query.newInstance().addParam("namespaceId", namespace)));
        publishPublicAgent(name, "1.0.0");
        Map<String, String> form = buildAgentCardForm(name, "2.0.0", "URL",
            buildV1AgentCard(name, "2.0.0", "1.0"));
        form.put("namespaceId", namespace);
        addCleanup(() -> deleteAgentDefinitionQuietly(namespace, name));
        postFormOk(ADMIN_A2A_PATH, form);
        Set<String> identifiers = new HashSet<>();
        for (String current : List.of(DEFAULT_NAMESPACE, namespace)) {
            Map<String, Object> request = searchRequest(name, TYPE_A2A);
            request.put("namespaceId", current);
            JsonNode body = null;
            for (int attempt = 0; attempt <= SEARCH_MAX_RETRIES; attempt++) {
                HttpResponse response = ardRawJson(JacksonUtils.toJson(request), "?namespaceId=" + current);
                assertEquals(200, response.code(), response.body());
                body = JacksonUtils.toObj(response.body());
                if (body.path("results").size() == 1) { break; }
                Thread.sleep(SEARCH_RETRY_INTERVAL_MILLIS);
            }
            assertEquals(Set.of(name), resourceNames(body.path("results")), body.toString());
            JsonNode entry = body.path("results").get(0);
            assertTrue(entry.path("url").asText().contains("namespaceId=" + current));
            identifiers.add(entry.path("identifier").asText());
        }
        assertEquals(2, identifiers.size());
        Map<String, Object> request = searchRequest(name, TYPE_A2A);
        request.put("namespaceId", namespace);
        assertArdInvalid(ardRawJson(JacksonUtils.toJson(request)));
        assertArdInvalid(ardRawJson(JacksonUtils.toJson(request), "?namespaceId=public"));
        if (AUTH_ENABLED) {
            HttpPost invalid = new HttpPost(ARD_BASE_URL + ARD_PATH + "/search");
            invalid.setEntity(new StringEntity("{", ContentType.APPLICATION_JSON));
            assertArdUnauthenticated(executeArdRaw(invalid, AuthIdentity.INVALID));
        }
    }

    @Test
    public void testExploreResolvesContextAndKeepsFacetOptionsIndependent() throws Exception {
        String marker = randomAiName("ard-explore");
        Set<String> names = Set.of(marker + "-ab", marker + "-a", marker + "-b");
        publishTaggedAgent(marker + "-ab", marker, List.of("a", "b"));
        publishTaggedAgent(marker + "-a", marker, List.of("a"));
        publishTaggedAgent(marker + "-b", marker, List.of("b"));
        awaitSearch(searchRequest(marker, TYPE_A2A), names);
        String ns = "https://agenticresourcediscovery.org/ns#";
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", Map.of("text", marker, "@context", Map.of("t", ns + "tags", "kind", ns + "mediaType"),
            "filter", Map.of("kind", TYPE_A2A)));
        request.put("resultType", Map.of("facets", List.of(Map.of("field", "type"), Map.of("field", "kind"),
            Map.of("field", "tags", "limit", 1), Map.of("field", "t", "limit", 2))));
        request.put("pageSize", 1);
        JsonNode response = ardPostJsonOk(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request);
        JsonNode facets = response.path("facets");
        assertEquals(facets.path("type"), facets.path("kind"));
        assertEquals(3, facets.path("kind").path("buckets").get(0).path("count").asInt());
        assertEquals(1, facets.path("tags").path("buckets").size());
        assertEquals(2, facets.path("tags").path("otherCount").asInt());
        assertEquals(2, facets.path("t").path("buckets").size());
        assertEquals(0, facets.path("t").path("otherCount").asInt());
        assertResponseSchemas("ExploreResponse", response);
        request.put("pageSize", 100);
        assertEquals(response, ardPostJsonOk(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request));
        request.put("resultType", Map.of("facets", List.of(Map.of("field", "t", "limit", 1),
            Map.of("field", "t", "limit", 2))));
        assertEquals(2, ardPostJsonOk(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request)
            .path("facets").path("t").path("buckets").size());
        for (Object query : List.of(Map.of("@context", 123), Map.of("filter", Map.of("tags", List.of(1))))) {
            request.put("query", query);
            assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request));
        }
        for (Object query : List.of(Map.of(), Map.of("text", " "))) {
            request.put("query", query);
            request.put("resultType", Map.of("facets", List.of(Map.of("field", "type"))));
            assertEquals(200, ardPostJsonRaw(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request).code());
        }
        request.remove("query");
        assertEquals(200, ardPostJsonRaw(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request).code());
        for (Object facetsRequest : List.of(List.of(), List.of(Map.of("field", " ")))) {
            request.put("resultType", Map.of("facets", facetsRequest));
            assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request));
        }
        request.remove("resultType");
        assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request));
    }

    private void publishTaggedAgent(String name, String marker, List<String> tags) throws Exception {
        publishPublicAgent(name, "1.0.0");
        Map<String, Object> update = agentUpdateRequest(DEFAULT_NAMESPACE, name, marker);
        update.put("tags", tags);
        putFormOk(ADMIN_AGENT_PATH, agentForm(update));
    }

    private void assertQueryNames(String text, Object context, Map<String, Object> filters,
        Object legacy, Set<String> expected) throws Exception {
        Map<String, Object> request = searchRequest(text, null);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("text", text);
        query.put("filter", filters);
        if (context != null) { query.put("@context", context); }
        if (legacy != null) { query.put("filters", legacy); }
        request.put("query", query);
        JsonNode response = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, request);
        assertEquals(expected, resourceNames(response.path("results")), response.toString());
        assertResponseSchemas("SearchResponse", response);
        for (JsonNode result : response.path("results")) {
            assertFalse(result.has("@context"));
            assertTrue(result.has("displayName") && result.has("type") && result.has("url"));
        }
    }

    private HttpResponse ardRawJson(String body) throws Exception {
        return ardRawJson(body, "");
    }

    private HttpResponse ardRawJson(String body, String query) throws Exception {
        HttpPost request = new HttpPost(ARD_BASE_URL + ARD_PATH + "/search" + query);
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        return executeArdRaw(request, AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS);
    }

    private void assertArdInvalid(HttpResponse response) {
        assertEquals(400, response.code(), response.body());
        JsonNode body = JacksonUtils.toObj(response.body());
        assertEquals("INVALID_ARGUMENT", body.path("errorCode").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertEquals(2, body.size());
    }

    @Test
    public void testRepresentativeQueriesPreserveSearchAndArtifactContent() throws Exception {
        String name = randomAiName("ard-query-examples");
        String lastQuery = "tailquery" + UUID.randomUUID().toString().replace("-", "");
        com.fasterxml.jackson.databind.node.ObjectNode card =
            (com.fasterxml.jackson.databind.node.ObjectNode) JacksonUtils.toObj(buildV1AgentCard(name, "1.0.0", "1.0"));
        var skill = card.putArray("skills").addObject();
        skill.put("id", "query-helper").put("name", "Query helper").put("description", "Find a capability");
        skill.putArray("tags").add("discovery");
        var examples = skill.putArray("examples");
        for (int i = 0; i < 5; i++) {
            examples.add("Find capability " + i);
        }
        examples.add(lastQuery);
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, name));
        postFormOk(ADMIN_A2A_PATH, buildAgentCardForm(name, "1.0.0", "URL", card.toString()));

        // The final example is outside the compatible five-item projection but remains searchable.
        JsonNode response = awaitSearch(searchRequest(lastQuery, TYPE_A2A), Set.of(name));
        JsonNode entry = findResource(response.path("results"), name);
        assertEquals(5, entry.path("representativeQueries").size(), entry.toString());
        assertFalse(entry.path("representativeQueries").toString().contains(lastQuery));
        assertResponseSchemas("SearchResponse", response);
        assertListRepresentation(name, TYPE_A2A);
        assertCatalogsContain(Set.of(name));
        JsonNode artifact = JacksonUtils.toObj(new String(
            ardGetBytesOk(entry.path("url").asText()).body(), StandardCharsets.UTF_8));
        assertEquals(6, artifact.path("skills").get(0).path("examples").size());
        assertTrue(artifact.path("skills").get(0).path("examples").toString().contains(lastQuery));

        AuthIdentity identity = AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS;
        JsonNode registry = discoveryGet("/.well-known/ard.json", identity).path("entries").get(0);
        assertEquals(List.of("Find agents, skills, prompts and MCP servers for a task",
            "Find AI resources by capability, type and tags"),
            JacksonUtils.toObj(registry.path("representativeQueries").toString(), List.class));
        assertFalse(discoveryGet("/.well-known/ai-catalog.json", identity)
            .path("entries").get(0).has("representativeQueries"));
    }

    @Test
    public void testWellKnownManifestAndLegacyCatalogIsolation() throws Exception {
        AuthIdentity identity = AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS;
        JsonNode legacyBefore = discoveryGet("/.well-known/ai-catalog.json", identity);
        JsonNode manifest = discoveryGet("/.well-known/ard.json", identity);
        var errors = CONTRACTS.validateEntry("ArdManifest", manifest);
        assertTrue(errors.isEmpty(), errors.toString());
        assertFalse(manifest.has("specVersion"));
        assertFalse(manifest.has("host"));
        assertEquals(1, manifest.path("entries").size());
        JsonNode registry = manifest.path("entries").get(0);
        assertEquals("application/ai-registry+json", registry.path("type").asText());
        assertEquals("https://agenticresourcediscovery.org/context/v1", registry.path("@context").asText());
        assertFalse(registry.has("score"));
        assertFalse(registry.has("source"));
        String base = registry.path("url").asText();
        assertTrue(URI.create(base).isAbsolute());
        assertFalse(base.endsWith("/search"), base);
        HttpPost search = new HttpPost(base + "/search?namespaceId=ard-empty-" + UUID.randomUUID());
        search.setEntity(new StringEntity(JacksonUtils.toJson(searchRequest("discovery", null)),
            ContentType.APPLICATION_JSON));
        HttpResponse response = executeArdRaw(search, identity);
        assertEquals(200, response.code(), response.body());
        JsonNode result = JacksonUtils.toObj(response.body());
        assertEquals(0, result.path("results").size());
        assertResponseSchemas("SearchResponse", result);
        JsonNode legacyAfter = discoveryGet("/.well-known/ai-catalog.json", identity);
        assertEquals(legacyBefore, legacyAfter);
        assertLegacyCatalog(legacyAfter);
        assertEquals("1.0", legacyAfter.path("specVersion").asText());
        assertTrue(legacyAfter.has("host"));
        assertFalse(legacyAfter.path("entries").get(0).has("@context"));
        assertEquals(registry.path("identifier"), legacyAfter.path("entries").get(0).path("identifier"));
        assertEquals(404, executeArdRaw(new HttpGet(ARD_BASE_URL + ARD_PATH + "/ard.json"), identity).code());
    }

    @Test
    public void testDiscoveryAuthenticationMatchesLegacyRoutes() throws Exception {
        for (String path : List.of("/.well-known/ard.json", "/.well-known/ai-catalog.json",
            ARD_PATH + "/ai-catalog.json")) {
            HttpResponse anonymous = executeArdRaw(new HttpGet(ARD_BASE_URL + path), AuthIdentity.ANONYMOUS);
            if (!AUTH_ENABLED || EXPECTED_ANONYMOUS_AI_ENABLED) {
                assertEquals(200, anonymous.code(), anonymous.body());
            } else {
                assertArdUnauthenticated(anonymous);
            }
            if (AUTH_ENABLED) {
                assertArdUnauthenticated(executeArdRaw(new HttpGet(ARD_BASE_URL + path), AuthIdentity.INVALID));
                assertEquals(200, executeArdRaw(new HttpGet(ARD_BASE_URL + path), AuthIdentity.ADMIN).code());
            }
        }
    }

    @Test
    public void testLegacyCatalogEnumeratesMoreThanOneHundredPublicResources() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        String prefix = randomAiName("ard-catalog");
        for (int i = 0; i < 105; i++) {
            String name = prefix + '-' + i;
            publishPublicAgent(name, "1.0.0");
            names.add(name);
        }
        AuthIdentity identity = AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS;
        String path = ARD_PATH + "/ai-catalog.json?namespaceId=" + DEFAULT_NAMESPACE;
        JsonNode catalog = null;
        for (int i = 0; i <= SEARCH_MAX_RETRIES; i++) {
            catalog = discoveryGet(path, identity);
            if (resourceNames(catalog.path("entries")).containsAll(names)) {
                break;
            }
            if (i < SEARCH_MAX_RETRIES) {
                Thread.sleep(SEARCH_RETRY_INTERVAL_MILLIS);
            }
        }
        assertTrue(resourceNames(catalog.path("entries")).containsAll(names), catalog.toString());
        assertLegacyCatalog(catalog);
        JsonNode empty = discoveryGet(ARD_PATH + "/ai-catalog.json?namespaceId=ard-other-" + UUID.randomUUID(), identity);
        assertEquals(1, empty.path("entries").size(), empty.toString());
        assertLegacyCatalog(empty);
        assertEquals(1, discoveryGet("/.well-known/ard.json", identity).path("entries").size());
        assertEquals(1, discoveryGet("/.well-known/ai-catalog.json", identity).path("entries").size());
        assertSearchAndListPageBoundaries(prefix, names);
    }

    private void assertSearchAndListPageBoundaries(String prefix, Set<String> expected) throws Exception {
        String filter = "type = '" + TYPE_A2A + "' AND displayName = '" + prefix
            + "' AND createdAfter > '2020-01-01' AND updatedAfter > '2020-01-01T00:00:00Z'";
        for (Integer size : Arrays.asList(null, -1, 0, 1, 50, 51, 100, 101, Integer.MAX_VALUE)) {
            Map<String, Object> search = searchRequest(prefix, TYPE_A2A);
            search.put("pageSize", size);
            JsonNode first = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, search);
            assertEquals(size == null || size <= 0 ? 10 : Math.min(size, 100), first.path("results").size());
            assertResponseSchemas("SearchResponse", first);
            Set<String> found = resourceNames(first.path("results"));
            String token = first.path("pageToken").asText();
            for (int page = 0; !token.isBlank() && page < 4; page++) {
                search.put("pageToken", token);
                search.put("pageSize", 51);
                JsonNode next = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, search);
                Set<String> current = resourceNames(next.path("results"));
                assertTrue(Collections.disjoint(found, current));
                found.addAll(current);
                token = next.path("pageToken").asText("");
            }
            assertTrue(token.isBlank());
            assertEquals(expected, found);

            Query list = Query.newInstance().addParam("filter", filter).addParam("orderBy", "displayName asc");
            if (size != null) { list.addParam("pageSize", size.toString()); }
            JsonNode listed = ardGetJsonOk(ARD_PATH + "/agents", list);
            assertEquals(size == null || size <= 0 ? 20 : Math.min(size, 100), listed.path("items").size());
            assertResponseSchemas("ListResponse", listed);
            List<String> ordered = new ArrayList<>(resourceNames(listed.path("items")));
            token = listed.path("pageToken").asText("");
            for (int page = 0; !token.isBlank() && page < 4; page++) {
                list.addParam("pageToken", token).addParam("pageSize", "51");
                JsonNode next = ardGetJsonOk(ARD_PATH + "/agents", list);
                Set<String> current = resourceNames(next.path("items"));
                assertTrue(Collections.disjoint(ordered, current));
                ordered.addAll(current);
                token = next.path("pageToken").asText("");
            }
            List<String> sorted = new ArrayList<>(expected);
            Collections.sort(sorted);
            assertTrue(token.isBlank());
            assertEquals(sorted, ordered);
        }
        for (Object size : List.of("51", 51.9)) {
            Map<String, Object> search = searchRequest(prefix, TYPE_A2A);
            search.put("pageSize", size);
            assertEquals(51, ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, search).path("results").size());
        }
        for (Object size : List.of(true, Map.of(), 2147483648L)) {
            Map<String, Object> search = searchRequest(prefix, TYPE_A2A);
            search.put("pageSize", size);
            assertArdInvalid(ardPostJsonRaw(ARD_PATH + "/search", DEFAULT_NAMESPACE, search));
        }
        for (String size : List.of("10.5", "true", "2147483648")) {
            HttpResponse invalid = executeExternalRaw(new HttpGet(ardUrl(ARD_PATH + "/agents",
                Query.newInstance().addParam("pageSize", size))));
            // Existing GET converter error mapping is recorded separately, not changed by the cap fix.
            assertEquals(500, invalid.code(), invalid.body());
            assertEquals("INTERNAL_ERROR", JacksonUtils.toObj(invalid.body()).path("errorCode").asText());
        }
        Query list = Query.newInstance().addParam("filter", filter).addParam("orderBy", "displayName asc")
            .addParam("pageSize", "1");
        JsonNode first = ardGetJsonOk(ARD_PATH + "/agents", list);
        String token = first.path("pageToken").asText();
        String anchor = first.path("items").get(0).path("metadata").path("resourceName").asText();
        list.addParam("pageToken", "invalid");
        assertArdInvalid(executeExternalRaw(new HttpGet(ardUrl(ARD_PATH + "/agents", list))));
        deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, anchor);
        list.addParam("pageToken", token);
        assertArdInvalid(executeExternalRaw(new HttpGet(ardUrl(ARD_PATH + "/agents", list))));
        Set<String> remaining = new LinkedHashSet<>(expected);
        remaining.remove(anchor);
        Query restarted = Query.newInstance().addParam("filter", filter).addParam("orderBy", "name sideways ignored")
            .addParam("pageSize", "100");
        Set<String> recovered = new LinkedHashSet<>();
        for (int page = 0; page < 3; page++) {
            JsonNode next = ardGetJsonOk(ARD_PATH + "/agents", restarted);
            Set<String> current = resourceNames(next.path("items"));
            assertTrue(Collections.disjoint(recovered, current));
            recovered.addAll(current);
            if (!next.hasNonNull("pageToken")) { break; }
            restarted.addParam("pageToken", next.path("pageToken").asText());
        }
        assertEquals(remaining, recovered);
        assertArdInvalid(executeExternalRaw(new HttpGet(ardUrl(ARD_PATH + "/agents",
            Query.newInstance().addParam("filter", "displayName > 'invalid'")))));
    }

    private JsonNode discoveryGet(String path, AuthIdentity identity) throws Exception {
        HttpResponse response = executeArdRaw(new HttpGet(ARD_BASE_URL + path), identity);
        assertEquals(200, response.code(), response.body());
        return JacksonUtils.toObj(response.body());
    }

    private HttpResponse executeArdRaw(org.apache.hc.core5.http.ClassicHttpRequest request,
        AuthIdentity identity) throws Exception {
        URI uri = request.getUri();
        URI allowed = URI.create(ARD_BASE_URL);
        assertEquals(allowed.getScheme(), uri.getScheme());
        assertEquals(allowed.getRawAuthority(), uri.getRawAuthority());
        assertTrue(uri.getPath().startsWith(ARD_PATH + "/")
            || List.of("/.well-known/ard.json", "/.well-known/ai-catalog.json").contains(uri.getPath()));
        return executeRaw(request, identity);
    }

    private void assertArdUnauthenticated(HttpResponse response) throws Exception {
        assertEquals(401, response.code(), response.body());
        JsonNode body = JacksonUtils.toObj(response.body());
        assertEquals("UNAUTHENTICATED", body.path("errorCode").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertFalse(body.has("code"));
        assertFalse(body.has("data"));
    }

    private void assertLegacyCatalog(JsonNode catalog) {
        var errors = CONTRACTS.validateLegacyCatalog(catalog);
        assertTrue(errors.isEmpty(), errors.toString());
        for (JsonNode entry : catalog.path("entries")) {
            assertFalse(entry.has("@context"));
            assertFalse(entry.has("score"));
            assertFalse(entry.has("source"));
        }
    }

    private void assertResponseSchemas(String definition, JsonNode response) {
        for (String version : List.of(ArdContractSchemas.LEGACY, ArdContractSchemas.CURRENT)) {
            var errors = CONTRACTS.validateResponse(version, definition, response);
            assertTrue(errors.isEmpty(), errors.toString());
        }
    }
    
    @Test
    public void testSearchEnhancementStatusAndCursorBoundary() throws Exception {
        String emptyNamespace = "jev-empty-" + UUID.randomUUID();
        Map<String, Object> request = searchRequest("jev capability", null);
        HttpPost post = new HttpPost(ardUrl(ARD_PATH + "/search",
            Query.newInstance().addParam("namespaceId", emptyNamespace)));
        AuthIdentity identity = AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS;
        authHeader(identity).getHeader().forEach(post::setHeader);
        post.setEntity(new StringEntity(JacksonUtils.toJson(request), ContentType.APPLICATION_JSON));
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient client =
            org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            client.execute(post, response -> {
                assertEquals(200, response.getCode());
                assertNotNull(response.getFirstHeader("X-Nacos-ARD-Enhancement"));
                assertEquals(System.getProperty("nacos.test.ard.relevance.empty-status", "disabled"),
                    response.getFirstHeader("X-Nacos-ARD-Enhancement").getValue());
                JsonNode body = JacksonUtils.toObj(org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity()));
                assertTrue(body.has("results"));
                assertFalse(body.has("enhancementStatus"));
                return null;
            });
        }
        request.put("pageToken", "invalid-enhancement-cursor");
        HttpPost invalid = new HttpPost(post.getUri());
        invalid.setEntity(new StringEntity(JacksonUtils.toJson(request), ContentType.APPLICATION_JSON));
        assertEquals(400, executeRaw(invalid, identity).code());
    }

    @Disabled("DAUTH-F03: private shared-index projection is incomplete with auth enabled; "
            + "see UNEXPECTED_PRODUCT_FINDINGS.md")
    @Test
    public void testLiveAdaptorUsesSharedIndexAndExactArtifacts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ArdFixture fixture = publishFixture(suffix);
        Set<String> expectedNames = fixture.allResourceNames();
        
        JsonNode defaultSearch = awaitSearch(searchRequest(suffix, null), expectedNames);
        assertEquals(expectedNames, resourceNames(defaultSearch.get("results")),
                defaultSearch.toString());
        JsonNode pureDefault = findResource(defaultSearch.get("results"), fixture.pureA2aAgent);
        JsonNode multiDefault = findResource(defaultSearch.get("results"), fixture.multiAgent);
        JsonNode latestCustomDefault = findResource(defaultSearch.get("results"),
                fixture.latestCustomAgent);
        assertEquals(TYPE_A2A, pureDefault.get("type").asText(), pureDefault.toString());
        assertEquals(TYPE_NACOS_AGENT, multiDefault.get("type").asText(),
                multiDefault.toString());
        assertEquals(TYPE_NACOS_AGENT, latestCustomDefault.get("type").asText(),
                latestCustomDefault.toString());
        assertEquals(6, defaultSearch.get("results").size(), defaultSearch.toString());
        
        JsonNode a2aSearch = awaitSearch(searchRequest(suffix, TYPE_A2A),
                Set.of(fixture.pureA2aAgent, fixture.multiAgent));
        assertEquals(Set.of(fixture.pureA2aAgent, fixture.multiAgent),
                resourceNames(a2aSearch.get("results")), a2aSearch.toString());
        JsonNode multiA2a = findResource(a2aSearch.get("results"), fixture.multiAgent);
        assertEquals(TYPE_A2A, multiA2a.get("type").asText(), multiA2a.toString());
        assertEquals(multiDefault.get("identifier").asText(),
                multiA2a.get("identifier").asText());
        assertNotEquals(multiDefault.get("url").asText(), multiA2a.get("url").asText());
        
        JsonNode nacosSearch = awaitSearch(searchRequest(suffix, TYPE_NACOS_AGENT),
                Set.of(fixture.pureA2aAgent, fixture.multiAgent, fixture.latestCustomAgent));
        assertEquals(Set.of(fixture.pureA2aAgent, fixture.multiAgent,
                        fixture.latestCustomAgent), resourceNames(nacosSearch.get("results")),
                nacosSearch.toString());
        
        assertListRepresentation(fixture.multiAgent, TYPE_A2A);
        assertExploreFacetCounts(suffix);
        assertCatalogsContain(expectedNames);
        assertAgentArtifacts(multiA2a, multiDefault, fixture.multiAgent);
        assertSkillArtifact(findResource(defaultSearch.get("results"), fixture.skillName),
                fixture);
        assertExternalErrorShape(suffix);
    }
    
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    public void testPublicAgentIndexAndUnifiedArtifacts(boolean clientPublication) throws Exception {
        String agentName = randomAiName("ard-public-model");
        if (clientPublication) {
            addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
            for (String version : new String[] {"0.9.0", "1.0.0"}) {
                Map<String, Object> request = agentInitialDraftRequest(null, agentName, version);
                request.put("callInterfaces", Arrays.asList(a2aCallInterface(agentName, version),
                        customCallInterface(agentName, version)));
                request.put("autoSubmit", !"0.9.0".equals(version));
                JsonNode published = postFormOk(nacosPath("/v3/client/ai/agents"), agentForm(request)).get("data");
                assertEquals("online", published.get("status").asText());
            }
        } else {
            publishPublicAgent(agentName, "0.9.0");
            publishNextAgentVersion(agentName, "1.0.0",
                    Arrays.asList(a2aCallInterface(agentName, "1.0.0"),
                            customCallInterface(agentName, "1.0.0")));
        }
        JsonNode nacosSearch = awaitSearch(searchRequest(agentName, TYPE_NACOS_AGENT),
                Set.of(agentName));
        JsonNode nacosResult = findResource(nacosSearch.get("results"), agentName);
        JsonNode a2aSearch = awaitSearch(searchRequest(agentName, TYPE_A2A), Set.of(agentName));
        JsonNode a2aResult = findResource(a2aSearch.get("results"), agentName);
        assertResponseSchemas("SearchResponse", nacosSearch);
        assertResponseSchemas("SearchResponse", a2aSearch);
        assertListRepresentation(agentName, TYPE_A2A);
        assertCatalogsContain(Set.of(agentName));
        assertAgentArtifacts(a2aResult, nacosResult, agentName);
        JsonNode artifact = JacksonUtils.toObj(new String(
                ardGetBytesOk(nacosResult.get("url").asText()).body(), StandardCharsets.UTF_8));
        JsonNode detail = getJsonOk(ADMIN_AGENT_PATH + "/version",
                agentVersionIdentityQuery(DEFAULT_NAMESPACE, agentName, "1.0.0")).get("data");
        assertEquals(detail.get("contentDigest"), artifact.get("contentDigest"));
        for (JsonNode callInterface : artifact.get("callInterfaces")) {
            assertFalse(callInterface.has("declaredEndpoints"), callInterface.toString());
            assertEquals(1, callInterface.get("endpointSets").size(), callInterface.toString());
            JsonNode set = callInterface.get("endpointSets").get(0);
            assertEquals("DECLARED", set.get("source").asText(), set.toString());
            assertFalse(set.hasNonNull("sourceRevision"), set.toString());
            assertFalse(set.hasNonNull("lastUpdatedTime"), set.toString());
            assertTrue(set.get("endpoints").size() > 0, set.toString());
            for (JsonNode endpoint : set.get("endpoints")) {
                assertTrue(endpoint.path("healthy").asBoolean(), endpoint.toString());
                assertTrue(endpoint.path("enabled").asBoolean(), endpoint.toString());
                assertEquals(0, endpoint.path("priority").asInt());
                assertEquals(1D, endpoint.path("weight").asDouble());
                for (String field : Arrays.asList("bindings", "state", "endpoint")) {
                    assertFalse(endpoint.hasNonNull(field), endpoint.toString());
                }
            }
        }
        postFormOk(ADMIN_AGENT_PATH + "/offline",
                agentForm(agentVersionCommand(null, agentName, "1.0.0")));
        HttpResponse offline = executeExternalRaw(new HttpGet(nacosResult.get("url").asText()));
        assertEquals(404, offline.code(), offline.body());
    }

    private ArdFixture publishFixture(String suffix) throws Exception {
        ArdFixture fixture = new ArdFixture();
        fixture.pureA2aAgent = "oit-ard-a2a-" + suffix;
        publishInitialAgent(fixture.pureA2aAgent, "1.0.0",
                Collections.singletonList(a2aCallInterface(fixture.pureA2aAgent, "1.0.0")));
        
        fixture.multiAgent = "oit-ard-multi-" + suffix;
        publishInitialAgent(fixture.multiAgent, "1.0.0",
                Arrays.asList(a2aCallInterface(fixture.multiAgent, "1.0.0"),
                        customCallInterface(fixture.multiAgent, "1.0.0")));
        
        fixture.latestCustomAgent = "oit-ard-latest-" + suffix;
        publishInitialAgent(fixture.latestCustomAgent, "1.0.0",
                Collections.singletonList(
                        a2aCallInterface(fixture.latestCustomAgent, "1.0.0")));
        publishNextAgentVersion(fixture.latestCustomAgent, "2.0.0",
                Collections.singletonList(
                        customCallInterface(fixture.latestCustomAgent, "2.0.0")));
        
        fixture.skillName = "oit-ard-skill-" + suffix;
        fixture.skillBody = "ARD shared-index skill " + suffix;
        fixture.skillGuide = "ARD shared-index guide " + suffix;
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(fixture.skillName,
                "1.0.0", fixture.skillBody, fixture.skillGuide));
        addCleanup(() -> deleteSkillQuietly(fixture.skillName));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish",
                skillPublishForm(fixture.skillName, "1.0.0"));
        grantAnonymousReadVisibility("skill", fixture.skillName);
        
        fixture.promptKey = "oit_ard_prompt_" + suffix;
        postFormOk(ADMIN_PROMPT_PATH + "/draft", promptDraftForm(fixture.promptKey,
                "1.0.0", "ARD prompt " + suffix, "ARD Prompt " + suffix,
                "openapi-it," + suffix));
        addCleanup(() -> deletePromptQuietly(fixture.promptKey));
        postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
                promptPublishForm(fixture.promptKey, "1.0.0"));
        grantAnonymousReadVisibility("prompt", fixture.promptKey);
        
        fixture.mcpName = "oit-ard-mcp-" + suffix;
        JsonNode created = postFormOk(ADMIN_MCP_PATH, mcpServerForm(fixture.mcpName,
                "1.0.0", "ARD MCP " + suffix, "tool_" + suffix,
                "resource_" + suffix));
        fixture.mcpId = created.get("data").asText();
        assertFalse(fixture.mcpId.isBlank(), created.toString());
        addCleanup(() -> deleteMcpServerQuietly(fixture.mcpName, fixture.mcpId));
        putFormOk(ADMIN_MCP_PATH, mcpServerForm(fixture.mcpName, "1.0.0",
                "ARD MCP " + suffix, "tool_" + suffix, "resource_" + suffix));
        grantAnonymousReadVisibility("mcp", fixture.mcpName);
        return fixture;
    }
    
    private void publishInitialAgent(String agentName, String version,
            List<Map<String, Object>> callInterfaces) throws Exception {
        Map<String, Object> request = agentInitialDraftRequest(null, agentName, version);
        request.put("callInterfaces", callInterfaces);
        postFormOk(ADMIN_AGENT_PATH + "/draft", agentForm(request));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, agentName, version)));
        grantAnonymousReadVisibility("agent", agentName);
    }
    
    private void publishNextAgentVersion(String agentName, String version,
            List<Map<String, Object>> callInterfaces) throws Exception {
        Map<String, Object> request = agentDraftCreateRequest(null, agentName, version, null);
        request.put("callInterfaces", callInterfaces);
        postFormOk(ADMIN_AGENT_PATH + "/draft", agentForm(request));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, agentName, version)));
    }
    
    private Map<String, Object> a2aCallInterface(String agentName, String version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", "a2a");
        result.put("protocolVersion", "1.0");
        result.put("descriptorMediaType", "application/json");
        result.put("nativeDescriptor",
                JacksonUtils.toObj(buildV1AgentCard(agentName, version, "1.0"), Map.class));
        result.put("endpointSourceOrder", Arrays.asList("DECLARED", "RUNTIME"));

        Map<String, Object> declaredSet1 = new LinkedHashMap<>();
        declaredSet1.put("source", "DECLARED");
        declaredSet1.put("endpoints", Arrays.asList(
                declaredEndpoint(agentName, "jsonrpc", "JSONRPC"),
                declaredEndpoint(agentName, "grpc", "GRPC")));
        result.put("endpointSets", java.util.Collections.singletonList(declaredSet1));
        return result;
    }
    
    private Map<String, Object> customCallInterface(String agentName, String version) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", agentName);
        descriptor.put("version", version);
        descriptor.put("description", "custom protocol " + agentName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", "custom-rpc");
        result.put("protocolVersion", "1.0");
        result.put("descriptorMediaType", "application/json");
        result.put("nativeDescriptor", descriptor);
        result.put("endpointSourceOrder", Arrays.asList("DECLARED", "RUNTIME"));

        Map<String, Object> declaredSet2 = new LinkedHashMap<>();
        declaredSet2.put("source", "DECLARED");
        declaredSet2.put("endpoints", Collections.singletonList(declaredEndpoint(agentName, "custom", "HTTP")));
        result.put("endpointSets", java.util.Collections.singletonList(declaredSet2));
        return result;
    }
    
    private Map<String, Object> declaredEndpoint(String agentName, String path,
            String transport) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", "https://example.com/" + agentName + '/' + path);
        result.put("transport", transport);
        return result;
    }
    
    private Map<String, Object> searchRequest(String text, String type) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("text", text);
        if (type != null) {
            query.put("filter", Collections.singletonMap("type", type));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("federation", "none");
        request.put("pageSize", 50);
        return request;
    }
    
    private JsonNode awaitSearch(Map<String, Object> request, Set<String> expectedNames)
            throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry <= SEARCH_MAX_RETRIES; retry++) {
            last = ardPostJsonOk(ARD_PATH + "/search", DEFAULT_NAMESPACE, request);
            if (resourceNames(last.get("results")).containsAll(expectedNames)) {
                return last;
            }
            if (retry < SEARCH_MAX_RETRIES) {
                Thread.sleep(SEARCH_RETRY_INTERVAL_MILLIS);
            }
        }
        fail("ARD shared-index projection did not converge: " + last);
        return last;
    }
    
    private void assertListRepresentation(String agentName, String type) throws Exception {
        Query query = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("filter", "metadata.resourceName = '" + agentName
                        + "' AND type = '" + type + "'")
                .addParam("orderBy", "identifier asc").addParam("pageSize", "10");
        JsonNode list = ardGetJsonOk(ARD_PATH + "/agents", query);
        assertResponseSchemas("ListResponse", list);
        assertEquals(1, list.get("items").size(), list.toString());
        JsonNode item = list.get("items").get(0);
        assertEquals(agentName, item.get("metadata").get("resourceName").asText(),
                item.toString());
        assertEquals(type, item.get("type").asText(), item.toString());
    }
    
    private void assertExploreFacetCounts(String suffix) throws Exception {
        Map<String, Object> facet = new LinkedHashMap<>();
        facet.put("field", "type");
        facet.put("limit", 10);
        facet.put("minCount", 1);
        Map<String, Object> query = Collections.singletonMap("text", suffix);
        Map<String, Object> resultType =
                Collections.singletonMap("facets", Collections.singletonList(facet));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("resultType", resultType);
        JsonNode response = ardPostJsonOk(ARD_PATH + "/explore", DEFAULT_NAMESPACE, request);
        assertEquals("facets", response.get("resultType").asText(), response.toString());
        Map<String, Integer> counts = facetCounts(response.get("facets").get("type"));
        assertEquals(1, counts.get(TYPE_A2A), response.toString());
        assertEquals(2, counts.get(TYPE_NACOS_AGENT), response.toString());
        assertEquals(1, counts.get(TYPE_SKILL), response.toString());
        assertEquals(1, counts.get(TYPE_PROMPT), response.toString());
        assertEquals(1, counts.get(TYPE_MCP), response.toString());
    }
    
    private void assertCatalogsContain(Set<String> expectedNames) throws Exception {
        JsonNode namespaceCatalog = ardGetJsonOk(ARD_PATH + "/ai-catalog.json",
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE));
        assertLegacyCatalog(namespaceCatalog);
        assertEquals("1.0", namespaceCatalog.get("specVersion").asText(),
                namespaceCatalog.toString());
        assertTrue(resourceNames(namespaceCatalog.get("entries")).containsAll(expectedNames),
                namespaceCatalog.toString());
        JsonNode hostCatalog = ardGetJsonOk("/.well-known/ai-catalog.json",
                Query.newInstance());
        assertEquals("1.0", hostCatalog.get("specVersion").asText(), hostCatalog.toString());
        assertEquals(1, hostCatalog.get("entries").size(), hostCatalog.toString());
        assertEquals("application/ai-registry+json",
                hostCatalog.get("entries").get(0).get("type").asText(),
                hostCatalog.toString());
    }
    
    private void assertAgentArtifacts(JsonNode a2aResult, JsonNode nacosResult,
            String agentName) throws Exception {
        ByteResponse a2a = ardGetBytesOk(a2aResult.get("url").asText());
        assertTrue(a2a.contentType().startsWith(TYPE_A2A), a2a.contentType());
        JsonNode card = JacksonUtils.toObj(new String(a2a.body(), StandardCharsets.UTF_8));
        assertEquals(agentName, card.get("name").asText(), card.toString());
        assertEquals("1.0.0", card.get("version").asText(), card.toString());
        
        ByteResponse nacos = ardGetBytesOk(nacosResult.get("url").asText());
        assertTrue(nacos.contentType().startsWith(TYPE_NACOS_AGENT), nacos.contentType());
        JsonNode artifact = JacksonUtils.toObj(
                new String(nacos.body(), StandardCharsets.UTF_8));
        assertEquals(agentName, artifact.get("agentName").asText(), artifact.toString());
        assertEquals("1.0.0", artifact.get("version").asText(), artifact.toString());
        assertEquals(2, artifact.get("callInterfaces").size(), artifact.toString());
        assertFalse(artifact.has("status"), artifact.toString());
        assertFalse(artifact.has("namespaceId"), artifact.toString());
    }
    
    private void assertSkillArtifact(JsonNode skillResult, ArdFixture fixture) throws Exception {
        ByteResponse skill = ardGetBytesOk(skillResult.get("url").asText());
        assertTrue(skill.contentType().startsWith(TYPE_SKILL), skill.contentType());
        Map<String, String> entries = unzipTextEntries(skill.body());
        String skillMd = entries.get(fixture.skillName + "/SKILL.md");
        assertNotNull(skillMd, entries.keySet().toString());
        assertTrue(skillMd.contains(fixture.skillBody), skillMd);
        assertEquals(fixture.skillGuide,
                entries.get(fixture.skillName + "/references/guide.md"));
    }
    
    private void assertExternalErrorShape(String suffix) throws Exception {
        Map<String, Object> request = searchRequest(suffix, null);
        request.put("namespaceId", "other");
        HttpResponse response = ardPostJsonRaw(ARD_PATH + "/search", DEFAULT_NAMESPACE,
                request);
        assertEquals(400, response.code(), response.body());
        JsonNode error = JacksonUtils.toObj(response.body());
        assertEquals("INVALID_ARGUMENT", error.get("errorCode").asText(), error.toString());
        assertTrue(error.get("message").asText().contains("namespaceId"), error.toString());
        assertEquals(2, error.size(), error.toString());
        assertFalse(error.has("code"), error.toString());
        assertFalse(error.has("data"), error.toString());
    }
    
    private JsonNode ardPostJsonOk(String path, String namespaceId, Map<String, Object> request)
            throws Exception {
        HttpResponse response = ardPostJsonRaw(path, namespaceId, request);
        assertEquals(200, response.code(), response.body());
        return JacksonUtils.toObj(response.body());
    }
    
    private HttpResponse ardPostJsonRaw(String path, String namespaceId,
            Map<String, Object> request) throws Exception {
        Query query = Query.newInstance();
        if (namespaceId != null) {
            query.addParam("namespaceId", namespaceId);
        }
        HttpPost post = new HttpPost(ardUrl(path, query));
        post.setEntity(new StringEntity(JacksonUtils.toJson(request),
                ContentType.APPLICATION_JSON));
        return executeExternalRaw(post);
    }
    
    private JsonNode ardGetJsonOk(String path, Query query) throws Exception {
        HttpResponse response = executeExternalRaw(new HttpGet(ardUrl(path, query)));
        assertEquals(200, response.code(), response.body());
        return JacksonUtils.toObj(response.body());
    }
    
    private ByteResponse ardGetBytesOk(String absoluteUrl) throws Exception {
        assertTrue(absoluteUrl.startsWith(ARD_BASE_URL + ARD_PATH + "/artifacts?"),
                absoluteUrl);
        ByteResponse response = executeExternalRawBytes(new HttpGet(absoluteUrl));
        assertEquals(200, response.code(),
                new String(response.body(), StandardCharsets.UTF_8));
        return response;
    }
    
    private String ardUrl(String path, Query query) {
        String queryString = query.toQueryUrl();
        return ARD_BASE_URL + path + (queryString.isEmpty() ? "" : "?" + queryString);
    }
    
    private Set<String> resourceNames(JsonNode items) {
        Set<String> result = new LinkedHashSet<>();
        if (items == null) {
            return result;
        }
        for (JsonNode item : items) {
            JsonNode name = item.path("metadata").path("resourceName");
            if (!name.isMissingNode()) {
                result.add(name.asText());
            }
        }
        return result;
    }
    
    private JsonNode findResource(JsonNode items, String resourceName) {
        for (JsonNode item : items) {
            if (resourceName.equals(item.path("metadata").path("resourceName").asText())) {
                return item;
            }
        }
        fail("ARD resource not found: " + resourceName + " in " + items);
        return null;
    }
    
    private Map<String, Integer> facetCounts(JsonNode facet) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (JsonNode bucket : facet.get("buckets")) {
            result.put(bucket.get("value").asText(), bucket.get("count").asInt());
        }
        return result;
    }
    
    private static final class ArdFixture {
        
        private String pureA2aAgent;
        
        private String multiAgent;
        
        private String latestCustomAgent;
        
        private String skillName;
        
        private String skillBody;
        
        private String skillGuide;
        
        private String promptKey;
        
        private String mcpName;
        
        private String mcpId;
        
        private Set<String> allResourceNames() {
            return Set.of(pureA2aAgent, multiAgent, latestCustomAgent, skillName, promptKey,
                    mcpName);
        }
    }
}
