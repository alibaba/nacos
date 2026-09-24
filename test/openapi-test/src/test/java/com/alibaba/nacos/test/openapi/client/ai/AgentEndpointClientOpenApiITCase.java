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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Agent HTTP Endpoint Publisher lifecycle.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: POST replaces one complete publication, heartbeat renews its
 *     Publisher, DELETE removes the complete publication, and retries remain idempotent. The
 *     same workflow cross-validates the Admin-created definition and Client publication through
 *     Admin, Console, and Client read surfaces while preserving the A2A {@code HTTP+JSON}
 *     transport.</li>
 *     <li>Boundary/validation: Search with the same Client id does not create a Publisher;
 *     Discover can reuse that id without changing the publication payload; stateful operations
 *     require a valid Client id and {@code Request-Module: AI}; registration validates its
 *     complete Form and JSON-valued {@code endpoints} field.</li>
 *     <li>Exception/error handling: heartbeat before registration and after deregistration
 *     returns HTTP 404 with application code {@code HTTP_CLIENT_NOT_FOUND (50404)}, and malformed
 *     Endpoint JSON is rejected by Form validation. The configured Endpoint soft watermark
 *     admits a whole batch from below even when it crosses the watermark; once at or above it,
 *     replacement remains available without growth, new publication is rejected atomically,
 *     and deregistration immediately releases capacity.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentEndpointClientOpenApiITCase extends AgentClientOpenApiBaseITCase {
    
    private static final String REQUEST_MODULE = "AI";

    private static final String SERVER_PUBLICATION_CAPACITY_PROPERTY =
            "nacos.agent.it.server.publication.capacity";

    private static final int DEFAULT_SERVER_PUBLICATION_CAPACITY = 100;
    
    @Test
    public void testA2aPublicMetadataAndInvalidReplacementAreAtomic() throws Exception {
        String name = randomAiName("a2a-public-metadata");
        String clientId = randomHttpClientId();
        publishAgent(name, "1.0.0");
        addCleanup(() -> deleteEndpointForm(clientId, REQUEST_MODULE, identityForm(name)));
        Map<String, String> form = registrationForm(name, 1);
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("uri", "http://127.0.0.1:18180/metadata");
        endpoint.put("transport", "HTTP+JSON");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("__nacos.agent.endpoint.protocolVersion__", "1.0");
        metadata.put("__nacos.agent.endpoint.tenant__", "tenant-a");
        endpoint.put("metadata", metadata);
        form.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        JsonNode initial = waitForEndpoint(clientId, name,
            value -> "tenant-a".equals(value.at("/metadata/__nacos.agent.endpoint.tenant__").asText()));
        assertEquals("1.0", initial.at("/metadata/__nacos.agent.endpoint.protocolVersion__").asText());
        for (String invalid : new String[] {null, "", "bad version", String.join("", Collections.nCopies(65, "x"))}) {
            metadata.put("__nacos.agent.endpoint.protocolVersion__", invalid);
            ObjectNode rawEndpoint = JacksonUtils.toObj(JacksonUtils.toJson(endpoint), ObjectNode.class);
            if (invalid == null) {
                ((ObjectNode) rawEndpoint.get("metadata")).putNull("__nacos.agent.endpoint.protocolVersion__");
            }
            form.put("endpoints", '[' + rawEndpoint.toString() + ']');
            assertError(postEndpointForm(clientId, REQUEST_MODULE, form), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "");
            JsonNode unchanged = waitForEndpoint(clientId, name, value -> value.has("metadata"));
            assertEquals("1.0", unchanged.at("/metadata/__nacos.agent.endpoint.protocolVersion__").asText());
            assertEquals("tenant-a", unchanged.at("/metadata/__nacos.agent.endpoint.tenant__").asText());
        }
        metadata.put("__nacos.agent.endpoint.protocolVersion__", "1.1");
        metadata.put("__nacos.agent.endpoint.tenant__", "");
        form.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        JsonNode changed = waitForEndpoint(clientId, name,
            value -> "1.1".equals(value.at("/metadata/__nacos.agent.endpoint.protocolVersion__").asText()));
        assertEquals("", changed.at("/metadata/__nacos.agent.endpoint.tenant__").asText());
        metadata.put("__nacos.agent.endpoint.path__", "spoof");
        form.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        assertError(postEndpointForm(clientId, REQUEST_MODULE, form), 400,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "");
    }

    @Test
    public void testEndpointBindingOverridesAndAtomicRejection() throws Exception {
        String agentName = randomAiName("endpoint-bindings");
        String clientId = randomHttpClientId();
        publishAgent(agentName, "1.0.0");
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, agentName, "2.0.0", "1.0.0")));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, agentName, "2.0.0")));
        addCleanup(() -> deleteEndpointForm(clientId, REQUEST_MODULE, identityForm(agentName)));
        Map<String, String> form = registrationForm(agentName, 2);
        form.put("versionRange", "[1.0.0,2.0.0]");
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> endpoint = new LinkedHashMap<>();
            endpoint.put("uri", "http://127.0.0.1:" + (18180 + i) + "/agent");
            endpoint.put("transport", "HTTP+JSON");
            endpoints.add(endpoint);
        }
        endpoints.get(1).put("bindings", Collections.singletonList(
                Collections.singletonMap("runtimeVersion", "2.0.0")));
        form.put("endpoints", JacksonUtils.toJson(endpoints));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        JsonNode actual = waitForRuntimeEndpointCount(clientId, agentName, 2);
        assertEquals("1.0.0", actual.at("/callInterfaces/0/endpointSets/0/endpoints/0/bindings/0/runtimeVersion").asText());
        assertEquals("2.0.0", actual.at("/callInterfaces/0/endpointSets/0/endpoints/1/bindings/0/runtimeVersion").asText());
        assertEquals("[1.0.0,2.0.0]", actual.at("/callInterfaces/0/endpointSets/0/endpoints/1/bindings/0/versionRange").asText());
        form.put("versionRange", "[1.0.0,2.0.0)");
        assertError(postEndpointForm(clientId, REQUEST_MODULE, form), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "versionRange");
        assertEquals(2, discover(clientId, agentName).at("/callInterfaces/0/endpointSets/0/endpoints").size());
        assertEquals("[1.0.0,2.0.0]", discover(clientId, agentName)
                .at("/callInterfaces/0/endpointSets/0/endpoints/0/bindings/0/versionRange").asText());
        form.remove("versionRange");
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        for (String invalid : new String[] {"[]", "[null]", "[{},{}]",
                "[{\"runtimeVersion\":\"\"}]", "[{\"versionRange\":\"bad\"}]"}) {
            endpoints.get(1).put("bindings", JacksonUtils.toObj(invalid));
            form.put("endpoints", JacksonUtils.toJson(endpoints));
            assertError(postEndpointForm(clientId, REQUEST_MODULE, form), 400,
                    ErrorCode.PARAMETER_VALIDATE_ERROR, "");
            assertEquals(2, discover(clientId, agentName).at("/callInterfaces/0/endpointSets/0/endpoints").size());
        }
        endpoints.get(0).put("bindings", Collections.singletonList(
                Collections.singletonMap("runtimeVersion", "1.0.0")));
        endpoints.get(1).put("bindings", Collections.singletonList(
                Collections.singletonMap("runtimeVersion", "2.0.0")));
        form.remove("runtimeVersion");
        form.put("endpoints", JacksonUtils.toJson(endpoints));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        JsonNode settled = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            settled = discover(clientId, agentName);
            if ("[2.0.0]".equals(settled.at("/callInterfaces/0/endpointSets/0/endpoints/1/bindings/0/versionRange").asText())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        assertEquals("[2.0.0]", settled.at("/callInterfaces/0/endpointSets/0/endpoints/1/bindings/0/versionRange").asText());
        HttpResponse exact = getWithClientId(AGENT_CLIENT_PATH, Query.newInstance()
                .addParam("agentName", agentName).addParam("version", "2.0.0"), clientId);
        assertEquals(200, exact.code(), exact.body());
        assertEquals(1, JacksonUtils.toObj(exact.body()).at("/data/callInterfaces/0/endpointSets/0/endpoints").size());
    }

    @Test
    public void testReportedHealthAndDefaultsAcrossReadSurfaces() throws Exception {
        String agentName = randomAiName("endpoint-health");
        String clientId = randomHttpClientId();
        publishAgent(agentName, "1.0.0");
        addCleanup(() -> deleteEndpointForm(clientId, REQUEST_MODULE, identityForm(agentName)));
        Map<String, String> form = registrationForm(agentName);
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("uri", "http://127.0.0.1:18080/agent");
        endpoint.put("transport", "HTTP+JSON");
        endpoint.put("healthy", false);
        endpoint.put("enabled", true);
        Map<String, String> forgedBinding = new LinkedHashMap<>();
        forgedBinding.put("runtimeVersion", "1.0.0");
        forgedBinding.put("versionRange", "[1.0.0]");
        endpoint.put("bindings", Collections.singletonList(forgedBinding));
        form.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        assertLiveness(heartbeat(clientId, REQUEST_MODULE));
        JsonNode unhealthy = waitForHealth(clientId, agentName, false);
        assertTrue(unhealthy.path("enabled").asBoolean(), unhealthy.toString());
        assertEquals(0, unhealthy.path("priority").asInt());
        assertEquals(1D, unhealthy.path("weight").asDouble());
        assertFalse(unhealthy.hasNonNull("state"), unhealthy.toString());
        assertEquals("1.0.0", unhealthy.at("/bindings/0/runtimeVersion").asText());
        assertEquals("[1.0.0]", unhealthy.at("/bindings/0/versionRange").asText());
        JsonNode runtime = getJsonOk(ADMIN_AGENT_PATH + "/runtime-endpoints",
                Query.newInstance().addParam("agentName", agentName).addParam("protocol", "a2a")).get("data");
        JsonNode set = runtime.at("/callInterface/endpointSets/0");
        assertEquals("RUNTIME", set.path("source").asText());
        assertTrue(set.path("lastUpdatedTime").isNumber(), runtime.toString());
        JsonNode managed = set.at("/endpoints/0");
        assertFalse(managed.has("state"));
        assertTrue(managed.path("enabled").asBoolean());
        assertFalse(managed.path("healthy").asBoolean());
        assertFalse(managed.has("endpoint"));
        endpoint.put("healthy", true);
        form.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        waitForHealth(clientId, agentName, true);
        endpoint.remove("healthy");
        endpoint.put("weight", 0D);
        endpoint.put("priority", 2147483647);
        form.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        JsonNode defaults = waitForEndpoint(clientId, agentName,
                value -> value.path("healthy").asBoolean()
                        && value.path("weight").isNumber() && value.path("weight").asDouble() == 0D
                        && value.path("priority").asInt() == 2147483647);
        assertEquals(0D, defaults.path("weight").asDouble());
        assertEquals(2147483647, defaults.path("priority").asInt());
    }

    @Test
    public void testEnabledReplacementNullRejectionAndConsoleState() throws Exception {
        String agentName = randomAiName("endpoint-enabled");
        String clientId = randomHttpClientId();
        publishAgent(agentName, "1.0.0");
        addCleanup(() -> deleteEndpointForm(clientId, REQUEST_MODULE, identityForm(agentName)));
        Map<String, String> form = registrationForm(agentName);
        String key = "\"uri\":\"http://127.0.0.1:18080/agent\",\"transport\":\"HTTP+JSON\"";
        form.put("endpoints", "[{" + key + ",\"enabled\":false,\"healthy\":false}]");
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        assertLiveness(heartbeat(clientId, REQUEST_MODULE));
        waitForRuntimeEndpointCount(clientId, agentName, 0);
        Query query = Query.newInstance().addParam("agentName", agentName).addParam("protocol", "a2a");
        JsonNode snapshot = getJsonOk(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH, query).get("data");
        JsonNode endpoint = snapshot.at("/callInterface/endpointSets/0/endpoints/0");
        assertFalse(endpoint.path("enabled").asBoolean(true), snapshot.toString());
        assertFalse(endpoint.path("healthy").asBoolean(true), snapshot.toString());
        assertFalse(endpoint.has("state"));
        JsonNode console = getConsoleJsonOk(CONSOLE_AGENT_RUNTIME_ENDPOINTS_PATH, query).get("data");
        JsonNode consoleEndpoint = console.at("/runtimeEndpointSnapshot/callInterface/endpointSets/0/endpoints/0");
        assertEquals(endpoint, consoleEndpoint);
        for (String field : new String[] {"priority", "weight", "healthy", "enabled"}) {
            form.put("endpoints", "[{" + key + ",\"" + field + "\":null}]");
            assertError(postEndpointForm(clientId, REQUEST_MODULE, form), 400,
                    ErrorCode.PARAMETER_VALIDATE_ERROR, field);
            JsonNode unchanged = getJsonOk(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH, query).get("data")
                    .at("/callInterface/endpointSets/0/endpoints/0");
            assertEquals(endpoint, unchanged, "invalid replacement must preserve publication");
        }
        form.put("endpoints", "[{" + key + ",\"enabled\":true,\"healthy\":false}]");
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        waitForHealth(clientId, agentName, false);
        form.put("endpoints", "[{" + key + "}]");
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, form));
        JsonNode defaults = waitForHealth(clientId, agentName, true);
        assertTrue(defaults.path("enabled").asBoolean());
    }

    private JsonNode waitForHealth(String clientId, String agentName, boolean healthy) throws Exception {
        return waitForEndpoint(clientId, agentName, value -> value.path("healthy").isBoolean()
                && value.path("healthy").asBoolean() == healthy);
    }

    private JsonNode waitForEndpoint(String clientId, String agentName, Predicate<JsonNode> matches)
            throws Exception {
        JsonNode actual = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            JsonNode endpoints = discover(clientId, agentName).at("/callInterfaces/0/endpointSets/0/endpoints");
            if (endpoints.size() == 1) {
                actual = endpoints.get(0);
                if (matches.test(actual)) {
                    return actual;
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected Endpoint values did not converge; last endpoint=" + actual);
    }

    @Test
    public void testCompletePublisherLifecycleAndQueryIsolation() throws Exception {
        String clientId = randomHttpClientId();
        HttpResponse query = getWithClientId(AGENT_SEARCH_PATH,
                Query.newInstance().addParam("agentNameContains", "none"), clientId);
        assertEquals(200, query.code(), query.body());
        assertError(heartbeat(clientId, REQUEST_MODULE), 404,
                ErrorCode.HTTP_CLIENT_NOT_FOUND, "HTTP Client");
        
        String agentName = randomAiName("agent-endpoint");
        publishAgent(agentName, "1.0.0");
        assertAgentVisibleThroughManagementSurfaces(agentName);
        addCleanup(() -> deleteEndpointForm(clientId, REQUEST_MODULE,
                identityForm(agentName)));
        Map<String, String> registration = registrationForm(agentName);
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, registration));
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE, registration));
        JsonNode discovered = waitForRuntimeEndpointCount(clientId, agentName, 1);
        JsonNode runtimeSet = discovered.get("callInterfaces").get(0)
                .get("endpointSets").get(0);
        assertEquals("RUNTIME", runtimeSet.get("source").asText(), runtimeSet.toString());
        assertEquals(1, runtimeSet.get("endpoints").size(), runtimeSet.toString());
        assertEquals("http://127.0.0.1:18080/agent",
                runtimeSet.get("endpoints").get(0).get("uri").asText(), runtimeSet.toString());
        assertEquals("HTTP+JSON",
                runtimeSet.get("endpoints").get(0).get("transport").asText(),
                runtimeSet.toString());
        assertTrue(runtimeSet.get("endpoints").get(0).get("healthy").asBoolean(),
                runtimeSet.toString());
        assertRuntimeEndpointVisibleThroughManagementSurfaces(agentName, 1);
        assertLiveness(heartbeat(clientId, REQUEST_MODULE));
        
        Query identity = identityForm(agentName);
        assertSuccessResponse(deleteEndpointForm(clientId, REQUEST_MODULE, identity));
        assertSuccessResponse(deleteEndpointForm(clientId, REQUEST_MODULE, identity));
        JsonNode afterDeregister = waitForRuntimeEndpointCount(null, agentName, 0);
        assertEquals(0, afterDeregister.get("callInterfaces").get(0)
                .get("endpointSets").get(0).get("endpoints").size(),
                afterDeregister.toString());
        assertRuntimeEndpointVisibleThroughManagementSurfaces(agentName, 0);
        assertError(heartbeat(clientId, REQUEST_MODULE), 404,
                ErrorCode.HTTP_CLIENT_NOT_FOUND, "HTTP Client");
    }
    
    @Test
    public void testEndpointHeadersBodyAndClientIdValidation() throws Exception {
        String agentName = randomAiName("agent-endpoint-invalid");
        Map<String, String> registration = registrationForm(agentName);
        assertError(postEndpointForm(null, REQUEST_MODULE, registration), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "X-Nacos-Client-Id");
        assertError(postEndpointForm(randomHttpClientId(), "NAMING", registration), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Request-Module");
        assertError(postEndpointForm("invalid client id", REQUEST_MODULE, registration), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "X-Nacos-Client-Id");
        
        Map<String, String> missingEndpoints = registrationForm(agentName);
        missingEndpoints.remove("endpoints");
        assertError(postEndpointForm(randomHttpClientId(), REQUEST_MODULE,
                missingEndpoints), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "endpoints");
        
        Map<String, String> malformedEndpoints = registrationForm(agentName);
        malformedEndpoints.put("endpoints", "{");
        HttpResponse malformed =
                postEndpointForm(randomHttpClientId(), REQUEST_MODULE, malformedEndpoints);
        assertEquals(400, malformed.code(), malformed.body());
        JsonNode malformedBody = JacksonUtils.toObj(malformed.body());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                malformedBody.get("code").asInt(), malformed.body());
        assertTrue(malformedBody.get("data").asText().contains("not valid JSON"),
                malformed.body());
        
        assertError(heartbeat(randomHttpClientId(), null), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Request-Module");
    }

    @Test
    public void testConfiguredPublicationCapacityAndSlotReuse() throws Exception {
        int serverCapacity = Integer.getInteger(SERVER_PUBLICATION_CAPACITY_PROPERTY,
                DEFAULT_SERVER_PUBLICATION_CAPACITY);
        String clientId = randomHttpClientId();
        String agentPrefix = randomAiName("agent-endpoint-capacity");
        List<String> admittedAgents = new ArrayList<>();
        addCleanup(() -> {
            for (String agentName : admittedAgents) {
                deleteEndpointForm(clientId, REQUEST_MODULE, identityForm(agentName));
            }
        });

        for (int i = 0; i < serverCapacity - 1; i++) {
            String agentName = agentPrefix + '-' + i;
            assertLiveness(postEndpointForm(clientId, REQUEST_MODULE,
                    registrationForm(agentName)));
            admittedAgents.add(agentName);
            if (i > 0 && i % 20 == 0) {
                assertLiveness(heartbeat(clientId, REQUEST_MODULE));
            }
        }

        String burstAgent = agentPrefix + "-burst";
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE,
                registrationForm(burstAgent, 3)));
        admittedAgents.add(burstAgent);
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE,
                registrationForm(burstAgent, 3)));
        String overflowAgent = agentPrefix + "-overflow";
        assertError(postEndpointForm(clientId, REQUEST_MODULE,
                registrationForm(overflowAgent)), 503,
                ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT,
                "limit of " + serverCapacity);

        assertSuccessResponse(deleteEndpointForm(clientId, REQUEST_MODULE,
                identityForm(burstAgent)));
        admittedAgents.remove(burstAgent);
        assertLiveness(postEndpointForm(clientId, REQUEST_MODULE,
                registrationForm(overflowAgent)));
        admittedAgents.add(overflowAgent);
    }
    
    private void assertLiveness(HttpResponse response) throws Exception {
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        JsonNode liveness = root.get("data");
        long heartbeatInterval = liveness.get("heartbeatIntervalMillis").asLong();
        long unhealthyTimeout = liveness.get("unhealthyTimeoutMillis").asLong();
        long expireTimeout = liveness.get("expireTimeoutMillis").asLong();
        assertTrue(heartbeatInterval < unhealthyTimeout, response.body());
        assertTrue(unhealthyTimeout < expireTimeout, response.body());
    }
    
    private void assertSuccessResponse(HttpResponse response) throws Exception {
        assertEquals(200, response.code(), response.body());
        assertSuccess(JacksonUtils.toObj(response.body()));
    }

    private void assertAgentVisibleThroughManagementSurfaces(String agentName) throws Exception {
        Query identity = agentIdentityQuery(null, agentName);
        JsonNode adminOverview = getJsonOk(ADMIN_AGENT_PATH, identity).get("data");
        assertOnlineOverview(adminOverview, agentName);
        JsonNode consoleOverview = getConsoleJsonOk(CONSOLE_AGENT_PATH, identity).get("data");
        assertOnlineOverview(consoleOverview, agentName);
    }

    private void assertOnlineOverview(JsonNode overview, String agentName) {
        assertEquals(agentName, overview.get("agent").get("agentName").asText(),
                overview.toString());
        assertEquals("online", overview.get("versionPage").get("pageItems").get(0)
                .get("status").asText(), overview.toString());
    }

    private void assertRuntimeEndpointVisibleThroughManagementSurfaces(String agentName,
            int expectedCount) throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName)
                .addParam("protocol", "a2a").addParam("version", "1.0.0");
        JsonNode adminSnapshot =
                getJsonOk(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH, query).get("data");
        assertRuntimeSnapshot(adminSnapshot, agentName, expectedCount);

        JsonNode consoleView =
                getConsoleJsonOk(CONSOLE_AGENT_RUNTIME_ENDPOINTS_PATH, query).get("data");
        assertRuntimeSnapshot(consoleView.get("runtimeEndpointSnapshot"), agentName,
                expectedCount);
        assertEquals("rad-" + agentName + "-a2a",
                consoleView.get("namingServiceRef").get("serviceName").asText(),
                consoleView.toString());
    }

    private void assertRuntimeSnapshot(JsonNode snapshot, String agentName, int expectedCount) {
        assertEquals(DEFAULT_NAMESPACE, snapshot.get("namespaceId").asText(),
                snapshot.toString());
        assertEquals(agentName, snapshot.get("agentName").asText(), snapshot.toString());
        assertEquals("a2a", snapshot.get("callInterface").get("protocol").asText(), snapshot.toString());
        assertEquals("1.0.0", snapshot.get("version").asText(), snapshot.toString());
        assertEquals(expectedCount, snapshot.get("callInterface").get("endpointSets").get(0).get("endpoints").size(), snapshot.toString());
        if (0 == expectedCount) {
            return;
        }
        JsonNode item = snapshot.get("callInterface").get("endpointSets").get(0).get("endpoints").get(0);
        assertEquals("http://127.0.0.1:18080/agent",
                item.get("uri").asText(), item.toString());
        assertEquals("HTTP+JSON", item.get("transport").asText(),
                item.toString());
        assertFalse(item.has("state"), item.toString());
        assertTrue(item.get("enabled").asBoolean(), item.toString());
        assertTrue(item.get("healthy").asBoolean(), item.toString());
        assertEquals("1.0.0", item.get("bindings").get(0).get("runtimeVersion").asText(),
                item.toString());
        assertEquals("[1.0.0]", item.get("bindings").get(0).get("versionRange").asText(),
                item.toString());
    }
    
    private JsonNode discover(String clientId, String agentName) throws Exception {
        HttpResponse response = getWithClientId(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName), clientId);
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root.get("data");
    }
    
    private JsonNode waitForRuntimeEndpointCount(String clientId, String agentName,
            int expectedCount) throws Exception {
        JsonNode actual = null;
        int retryTime = 20;
        while (retryTime-- > 0) {
            actual = discover(clientId, agentName);
            int count = actual.get("callInterfaces").get(0)
                    .get("endpointSets").get(0).get("endpoints").size();
            if (count == expectedCount) {
                return actual;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected " + expectedCount
                + " Runtime Endpoints, last discovery=" + actual);
    }
    
    private Map<String, String> registrationForm(String agentName) {
        return registrationForm(agentName, 1);
    }

    private Map<String, String> registrationForm(String agentName, int endpointCount) {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (int i = 0; i < endpointCount; i++) {
            Map<String, Object> endpoint = new LinkedHashMap<>();
            endpoint.put("uri", "http://127.0.0.1:" + (18080 + i) + "/agent");
            endpoint.put("transport", "HTTP+JSON");
            endpoint.put("priority", 0);
            endpoint.put("weight", 1.0D);
            endpoint.put("metadata", Collections.singletonMap("zone", "openapi-it"));
            endpoints.add(endpoint);
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("agentName", agentName);
        result.put("runtimeVersion", "1.0.0");
        result.put("versionRange", "[1.0.0]");
        result.put("protocol", "a2a");
        result.put("endpoints", JacksonUtils.toJson(endpoints));
        return result;
    }
    
    private Query identityForm(String agentName) {
        return Query.newInstance().addParam("agentName", agentName)
                .addParam("protocol", "a2a");
    }
}
