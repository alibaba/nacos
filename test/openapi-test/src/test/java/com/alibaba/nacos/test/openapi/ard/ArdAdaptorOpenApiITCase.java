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

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        
        fixture.promptKey = "oit_ard_prompt_" + suffix;
        postFormOk(ADMIN_PROMPT_PATH + "/draft", promptDraftForm(fixture.promptKey,
                "1.0.0", "ARD prompt " + suffix, "ARD Prompt " + suffix,
                "openapi-it," + suffix));
        addCleanup(() -> deletePromptQuietly(fixture.promptKey));
        postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
                promptPublishForm(fixture.promptKey, "1.0.0"));
        
        fixture.mcpName = "oit-ard-mcp-" + suffix;
        JsonNode created = postFormOk(ADMIN_MCP_PATH, mcpServerForm(fixture.mcpName,
                "1.0.0", "ARD MCP " + suffix, "tool_" + suffix,
                "resource_" + suffix));
        fixture.mcpId = created.get("data").asText();
        assertFalse(fixture.mcpId.isBlank(), created.toString());
        addCleanup(() -> deleteMcpServerQuietly(fixture.mcpName, fixture.mcpId));
        putFormOk(ADMIN_MCP_PATH, mcpServerForm(fixture.mcpName, "1.0.0",
                "ARD MCP " + suffix, "tool_" + suffix, "resource_" + suffix));
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
        result.put("declaredEndpoints", Arrays.asList(
                declaredEndpoint(agentName, "jsonrpc", "JSONRPC"),
                declaredEndpoint(agentName, "grpc", "GRPC")));
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
        result.put("declaredEndpoints",
                Collections.singletonList(declaredEndpoint(agentName, "custom", "HTTP")));
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
        return executeRaw(post);
    }
    
    private JsonNode ardGetJsonOk(String path, Query query) throws Exception {
        HttpResponse response = executeRaw(new HttpGet(ardUrl(path, query)));
        assertEquals(200, response.code(), response.body());
        return JacksonUtils.toObj(response.body());
    }
    
    private ByteResponse ardGetBytesOk(String absoluteUrl) throws Exception {
        assertTrue(absoluteUrl.startsWith(ARD_BASE_URL + ARD_PATH + "/artifacts?"),
                absoluteUrl);
        ByteResponse response = executeRawBytes(new HttpGet(absoluteUrl));
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
