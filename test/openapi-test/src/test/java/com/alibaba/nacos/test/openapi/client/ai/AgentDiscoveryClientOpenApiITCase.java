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

package com.alibaba.nacos.test.openapi.client.ai;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for RAD Search and Discover under {@code /nacos/v3/client/ai/agents}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: Search returns an enabled Agent's online Version catalog, while
 *     Discover resolves latest and returns the native protocol descriptor plus its authoritative
 *     Endpoint sets. During a two-Version rollout, omitted selection retains all online-Version
 *     Runtime Endpoints while explicit latest remains latest-only.</li>
 *     <li>Boundary/validation: omitted namespace uses {@code public}; Search filters by name,
 *     tags, and protocol; Discover supports protocol filtering and rejects mutually exclusive
 *     Version and label references.</li>
 *     <li>Exception/error handling: no-match Search returns an empty page, invalid pagination
 *     returns HTTP 400, and an absent Discover target returns a controlled not-found envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentDiscoveryClientOpenApiITCase extends AgentClientOpenApiBaseITCase {
    
    @Test
    public void testSearchAndDiscoverOnlineAgent() throws Exception {
        String agentName = randomAiName("agent-client-discovery");
        String version = "1.0.0";
        publishAgent(agentName, version);
        
        JsonNode search = waitForSearchTotal(Query.newInstance()
                .addParam("agentNameContains", agentName)
                .addParam("tagsAll", "openapi-it")
                .addParam("protocolsAny", "a2a")
                .addParam("pageNo", "1").addParam("pageSize", "10"), 1);
        JsonNode page = search.get("data");
        assertEmptyPageShape(page);
        assertEquals(1, page.get("totalCount").asInt(), page.toString());
        JsonNode catalog = findCatalog(page, agentName);
        assertFalse(catalog.isMissingNode(), page.toString());
        assertEquals(version, catalog.get("latestVersion").asText(), catalog.toString());
        assertEquals(version, catalog.get("versions").get(0).get("version").asText(),
                catalog.toString());
        assertEquals("a2a", catalog.get("versions").get(0).get("protocols").get(0).asText(),
                catalog.toString());
        
        JsonNode discovered = getJsonOk(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)).get("data");
        assertEquals(DEFAULT_NAMESPACE, discovered.get("namespaceId").asText(),
                discovered.toString());
        assertEquals(agentName, discovered.get("agentName").asText(), discovered.toString());
        assertEquals(version, discovered.get("version").asText(), discovered.toString());
        assertTrue(discovered.get("contentDigest").asText().startsWith("sha256:"),
                discovered.toString());
        JsonNode callInterface = discovered.get("callInterfaces").get(0);
        assertEquals("a2a", callInterface.get("protocol").asText(), callInterface.toString());
        assertEquals(agentName, callInterface.get("nativeDescriptor").get("name").asText(),
                callInterface.toString());
        assertEquals("RUNTIME", callInterface.get("endpointSets").get(0).get("source").asText(),
                callInterface.toString());
        assertEquals(0, callInterface.get("endpointSets").get(0).get("endpoints").size(),
                callInterface.toString());
        
        JsonNode filtered = getJsonOk(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("protocol", "jsonrpc")).get("data");
        assertEquals(0, filtered.get("callInterfaces").size(), filtered.toString());
    }
    
    @Test
    public void testSearchAndDiscoverValidationAndNotFound() throws Exception {
        String absentName = randomAiName("agent-client-absent");
        JsonNode empty = waitForSearch(Query.newInstance()
                .addParam("agentNameContains", absentName)).get("data");
        assertEquals(0, empty.get("totalCount").asInt(), empty.toString());
        assertEquals(0, empty.get("pageItems").size(), empty.toString());
        
        assertError(getRaw(AGENT_SEARCH_PATH + "?pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(AGENT_SEARCH_PATH + "?pageSize=101"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
        assertError(getRaw(AGENT_CLIENT_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "agentName");
        assertError(getRaw(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", absentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "agent not found");
        assertError(getRaw(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", absentName)
                        .addParam("version", "1.0.0").addParam("label", "stable")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "mutually exclusive");
    }

    @Test
    public void testDefaultRolloutPoolAndExplicitLatest() throws Exception {
        String agentName = randomAiName("agent-client-rollout");
        String versionOne = "1.0.0";
        String versionTwo = "2.0.0";
        String versionOneClient = randomHttpClientId();
        String versionTwoClient = randomHttpClientId();
        publishAgent(agentName, versionOne);
        JsonNode versionOneCatalog = waitForCatalog(agentName, versionOne, 1);
        assertEquals(versionOne,
                versionOneCatalog.get("versions").get(0).get("version").asText(),
                versionOneCatalog.toString());
        addCleanup(() -> deleteEndpointForm(versionOneClient, "AI",
                endpointIdentity(agentName)));
        addCleanup(() -> deleteEndpointForm(versionTwoClient, "AI",
                endpointIdentity(agentName)));

        assertEndpointRegistration(postEndpointForm(versionOneClient, "AI",
                endpointRegistration(agentName, versionOne, 18101)));
        JsonNode initialDefault = waitForRuntimeEndpointCount(agentName, null, null, 1);
        assertEquals(versionOne, initialDefault.get("version").asText(),
                initialDefault.toString());
        assertRuntimeEndpoint(initialDefault, versionOne, 18101);

        postFormOk(ADMIN_AGENT_PATH + "/draft", agentForm(
                agentDraftCreateRequest(null, agentName, versionTwo, null)));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish", agentForm(
                agentVersionCommand(null, agentName, versionTwo)));
        JsonNode versionTwoCatalog = waitForCatalog(agentName, versionTwo, 2);
        assertEquals(versionTwo,
                versionTwoCatalog.get("versions").get(0).get("version").asText(),
                versionTwoCatalog.toString());
        assertEquals(versionOne,
                versionTwoCatalog.get("versions").get(1).get("version").asText(),
                versionTwoCatalog.toString());

        JsonNode defaultBeforeVersionTwoEndpoint = waitForRuntimeEndpointCount(agentName,
                null, null, 1);
        JsonNode latestBeforeVersionTwoEndpoint = waitForRuntimeEndpointCount(agentName,
                null, "latest", 0);
        assertEquals(versionTwo, defaultBeforeVersionTwoEndpoint.get("version").asText(),
                defaultBeforeVersionTwoEndpoint.toString());
        assertEquals(versionTwo, latestBeforeVersionTwoEndpoint.get("version").asText(),
                latestBeforeVersionTwoEndpoint.toString());
        assertRuntimeEndpoint(defaultBeforeVersionTwoEndpoint, versionOne, 18101);
        assertNotEquals(runtimeEndpointSet(defaultBeforeVersionTwoEndpoint)
                        .get("sourceRevision").asText(),
                runtimeEndpointSet(latestBeforeVersionTwoEndpoint)
                        .get("sourceRevision").asText());

        assertEndpointRegistration(postEndpointForm(versionTwoClient, "AI",
                endpointRegistration(agentName, versionTwo, 18102)));
        assertEquals(versionTwoCatalog, waitForCatalog(agentName, versionTwo, 2),
                "Runtime Endpoint publication must not change the Search catalog");
        JsonNode combinedDefault = waitForRuntimeEndpointCount(agentName,
                null, null, 2);
        JsonNode latestOnly = waitForRuntimeEndpointCount(agentName,
                null, "latest", 1);
        JsonNode exactVersionOne = waitForRuntimeEndpointCount(agentName,
                versionOne, null, 1);
        assertRuntimeEndpoint(combinedDefault, versionOne, 18101);
        assertRuntimeEndpoint(combinedDefault, versionTwo, 18102);
        assertRuntimeEndpoint(latestOnly, versionTwo, 18102);
        assertRuntimeEndpoint(exactVersionOne, versionOne, 18101);
        assertFalse(hasRuntimeEndpoint(latestOnly, 18101), latestOnly.toString());
        assertFalse(hasRuntimeEndpoint(exactVersionOne, 18102), exactVersionOne.toString());

        postFormOk(ADMIN_AGENT_PATH + "/offline", agentForm(
                agentVersionCommand(null, agentName, versionOne)));
        JsonNode defaultAfterVersionOneOffline = waitForRuntimeEndpointCount(agentName,
                null, null, 1);
        JsonNode latestAfterVersionOneOffline = waitForRuntimeEndpointCount(agentName,
                null, "latest", 1);
        assertRuntimeEndpoint(defaultAfterVersionOneOffline, versionTwo, 18102);
        assertFalse(hasRuntimeEndpoint(defaultAfterVersionOneOffline, 18101),
                defaultAfterVersionOneOffline.toString());
        assertEquals(runtimeEndpointSet(latestAfterVersionOneOffline)
                        .get("sourceRevision").asText(),
                runtimeEndpointSet(defaultAfterVersionOneOffline)
                        .get("sourceRevision").asText());
        JsonNode onlyVersionTwoCatalog = waitForCatalog(agentName, versionTwo, 1);
        assertEquals(versionTwo,
                onlyVersionTwoCatalog.get("versions").get(0).get("version").asText(),
                onlyVersionTwoCatalog.toString());

        postFormOk(ADMIN_AGENT_PATH + "/offline", agentForm(
                agentVersionCommand(null, agentName, versionTwo)));
        waitForCatalogAbsent(agentName);
    }

    @Test
    public void testSearchProjectionFiltersLiteralNamesAndStablePagination() throws Exception {
        String stem = randomAiName("agent-search-index");
        List<String> expectedNames = new ArrayList<>(Arrays.asList(
                stem + "-%", stem + "-A", stem + "-B", stem + "-\\",
                stem + "-_", stem + "-Case"));
        publishSearchAgent(expectedNames.get(0), Arrays.asList("openapi-it", "other"),
                Collections.singletonList("a2a"));
        publishSearchAgent(expectedNames.get(1),
                Arrays.asList("openapi-it", "shared", "blue"),
                Collections.singletonList("a2a"));
        publishSearchAgent(expectedNames.get(2),
                Arrays.asList("openapi-it", "shared", "blue"),
                Collections.singletonList("mcp"));
        for (int i = 3; i < expectedNames.size(); i++) {
            publishSearchAgent(expectedNames.get(i),
                    Arrays.asList("openapi-it", "other"),
                    Collections.singletonList("a2a"));
        }
        Collections.sort(expectedNames);

        List<String> actualNames = new ArrayList<>();
        int expectedPages = (expectedNames.size() + 1) / 2;
        for (int pageNo = 1; pageNo <= expectedPages; pageNo++) {
            JsonNode page = waitForSearchTotal(Query.newInstance()
                    .addParam("agentNameContains", stem)
                    .addParam("pageNo", String.valueOf(pageNo))
                    .addParam("pageSize", "2"), expectedNames.size()).get("data");
            assertEquals(expectedNames.size(), page.get("totalCount").asInt(), page.toString());
            assertEquals(expectedPages, page.get("pagesAvailable").asInt(), page.toString());
            for (JsonNode item : page.get("pageItems")) {
                actualNames.add(item.get("agentName").asText());
            }
        }
        assertEquals(expectedNames, actualNames);
        JsonNode outOfRange = waitForSearch(Query.newInstance()
                .addParam("agentNameContains", stem)
                .addParam("pageNo", String.valueOf(expectedPages + 1))
                .addParam("pageSize", "2")).get("data");
        assertEquals(expectedNames.size(), outOfRange.get("totalCount").asInt(),
                outOfRange.toString());
        assertEquals(0, outOfRange.get("pageItems").size(), outOfRange.toString());

        assertLiteralSearch(stem, "%", stem + "-%");
        assertLiteralSearch(stem, "_", stem + "-_");
        assertLiteralSearch(stem, "\\", stem + "-\\");
        JsonNode caseSensitive = waitForSearch(Query.newInstance()
                .addParam("agentNameContains", stem + "-case")).get("data");
        assertEquals(0, caseSensitive.get("totalCount").asInt(), caseSensitive.toString());

        JsonNode combined = waitForSearch(Query.newInstance()
                .addParam("agentNameContains", stem)
                .addParam("tagsAll", "shared,blue")
                .addParam("protocolsAny", "mcp,jsonrpc")).get("data");
        assertEquals(1, combined.get("totalCount").asInt(), combined.toString());
        assertEquals(stem + "-B",
                combined.get("pageItems").get(0).get("agentName").asText(),
                combined.toString());
    }
    
    private JsonNode findCatalog(JsonNode page, String agentName) {
        for (JsonNode item : page.get("pageItems")) {
            if (agentName.equals(item.get("agentName").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }

    private void publishSearchAgent(String agentName, List<String> tags,
            List<String> protocols) throws Exception {
        Map<String, Object> draft = agentInitialDraftRequest(null, agentName, "1.0.0");
        draft.put("iconUrl", "https://example.com/agent-search-index/icon.png");
        draft.put("tags", tags);
        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>)
                ((List<?>) draft.get("callInterfaces")).get(0);
        List<Map<String, Object>> interfaces = new ArrayList<>();
        for (String protocol : protocols) {
            Map<String, Object> callInterface = new LinkedHashMap<>(template);
            callInterface.put("protocol", protocol);
            interfaces.add(callInterface);
        }
        draft.put("callInterfaces", interfaces);
        postFormOk(ADMIN_AGENT_PATH + "/draft", agentForm(draft));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish", agentForm(
                agentVersionCommand(null, agentName, "1.0.0")));
    }

    private void assertLiteralSearch(String stem, String literal, String expectedName)
            throws Exception {
        JsonNode page = waitForSearchTotal(Query.newInstance()
                .addParam("agentNameContains", stem + "-" + literal), 1).get("data");
        assertEquals(1, page.get("totalCount").asInt(), page.toString());
        assertEquals(expectedName, page.get("pageItems").get(0).get("agentName").asText(),
                page.toString());
    }

    private JsonNode waitForCatalog(String agentName, String expectedLatest,
            int expectedVersionCount) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry < 100; retry++) {
            JsonNode page = waitForSearch(Query.newInstance()
                    .addParam("agentNameContains", agentName)).get("data");
            last = findCatalog(page, agentName);
            if (!last.isMissingNode()
                    && expectedLatest.equals(last.path("latestVersion").asText())
                    && last.path("versions").size() == expectedVersionCount) {
                return last;
            }
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        throw new AssertionError("Expected Search catalog latest=" + expectedLatest
                + ", versions=" + expectedVersionCount + ", last=" + last);
    }

    private void waitForCatalogAbsent(String agentName) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry < 100; retry++) {
            JsonNode page = waitForSearch(Query.newInstance()
                    .addParam("agentNameContains", agentName)).get("data");
            last = findCatalog(page, agentName);
            if (last.isMissingNode()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        throw new AssertionError("Expected Agent to leave Search after all Versions offline: "
                + last);
    }

    private JsonNode waitForSearch(Query query) throws Exception {
        HttpResponse response = getRaw(AGENT_SEARCH_PATH, query);
        assertEquals(200, response.code(), response.body());
        JsonNode result = JacksonUtils.toObj(response.body());
        assertSuccess(result);
        return result;
    }

    private JsonNode waitForSearchTotal(Query query, int expectedTotal) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry < 100; retry++) {
            last = waitForSearch(query);
            if (last.path("data").path("totalCount").asInt() == expectedTotal) {
                return last;
            }
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        throw new AssertionError("Agent Search did not converge to total=" + expectedTotal
                + ", last=" + last);
    }

    private Map<String, String> endpointRegistration(String agentName, String runtimeVersion,
            int port) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("uri", "http://127.0.0.1:" + port + "/agent");
        endpoint.put("transport", "HTTP");
        endpoint.put("priority", 0);
        endpoint.put("weight", 1.0D);
        endpoint.put("metadata", Collections.singletonMap("version", runtimeVersion));

        Map<String, String> result = new LinkedHashMap<>();
        result.put("agentName", agentName);
        result.put("runtimeVersion", runtimeVersion);
        result.put("versionRange", "[" + runtimeVersion + "]");
        result.put("protocol", "a2a");
        result.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        return result;
    }

    private Query endpointIdentity(String agentName) {
        return Query.newInstance().addParam("agentName", agentName)
                .addParam("protocol", "a2a");
    }

    private void assertEndpointRegistration(HttpResponse response) throws Exception {
        assertEquals(200, response.code(), response.body());
        assertSuccess(JacksonUtils.toObj(response.body()));
    }

    private JsonNode waitForRuntimeEndpointCount(String agentName, String version, String label,
            int expectedCount) throws Exception {
        JsonNode actual = null;
        int retries = 50;
        while (retries-- > 0) {
            Query query = Query.newInstance().addParam("agentName", agentName);
            if (version != null) {
                query.addParam("version", version);
            }
            if (label != null) {
                query.addParam("label", label);
            }
            actual = getJsonOk(AGENT_CLIENT_PATH, query).get("data");
            if (runtimeEndpointSet(actual).get("endpoints").size() == expectedCount) {
                return actual;
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        throw new AssertionError("Expected " + expectedCount
                + " Runtime Endpoints, last discovery=" + actual);
    }

    private JsonNode runtimeEndpointSet(JsonNode discovery) {
        for (JsonNode callInterface : discovery.get("callInterfaces")) {
            if (!"a2a".equals(callInterface.get("protocol").asText())) {
                continue;
            }
            for (JsonNode endpointSet : callInterface.get("endpointSets")) {
                if ("RUNTIME".equals(endpointSet.get("source").asText())) {
                    return endpointSet;
                }
            }
        }
        throw new AssertionError("Runtime Endpoint set is missing: " + discovery);
    }

    private void assertRuntimeEndpoint(JsonNode discovery, String runtimeVersion, int port) {
        for (JsonNode endpoint : runtimeEndpointSet(discovery).get("endpoints")) {
            if (endpoint.get("uri").asText().contains(":" + port + "/")) {
                assertEquals(1, endpoint.get("bindings").size(), endpoint.toString());
                assertEquals(runtimeVersion,
                        endpoint.get("bindings").get(0).get("runtimeVersion").asText(),
                        endpoint.toString());
                assertEquals("[" + runtimeVersion + "]",
                        endpoint.get("bindings").get(0).get("versionRange").asText(),
                        endpoint.toString());
                return;
            }
        }
        throw new AssertionError("Runtime Endpoint for port " + port + " is missing: "
                + discovery);
    }

    private boolean hasRuntimeEndpoint(JsonNode discovery, int port) {
        for (JsonNode endpoint : runtimeEndpointSet(discovery).get("endpoints")) {
            if (endpoint.get("uri").asText().contains(":" + port + "/")) {
                return true;
            }
        }
        return false;
    }
}
