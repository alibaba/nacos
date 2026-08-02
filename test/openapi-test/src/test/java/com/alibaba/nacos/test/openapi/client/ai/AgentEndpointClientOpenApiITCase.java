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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 *     Endpoint JSON is rejected by Form validation.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentEndpointClientOpenApiITCase extends AgentClientOpenApiBaseITCase {
    
    private static final String REQUEST_MODULE = "AI";
    
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
        assertEquals("a2a", snapshot.get("protocol").asText(), snapshot.toString());
        assertEquals("1.0.0", snapshot.get("version").asText(), snapshot.toString());
        assertEquals(expectedCount, snapshot.get("items").size(), snapshot.toString());
        if (0 == expectedCount) {
            return;
        }
        JsonNode item = snapshot.get("items").get(0);
        assertEquals("http://127.0.0.1:18080/agent",
                item.get("endpoint").get("uri").asText(), item.toString());
        assertEquals("HTTP+JSON", item.get("endpoint").get("transport").asText(),
                item.toString());
        assertEquals("AVAILABLE", item.get("state").asText(), item.toString());
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
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("uri", "http://127.0.0.1:18080/agent");
        endpoint.put("transport", "HTTP+JSON");
        endpoint.put("priority", 0);
        endpoint.put("weight", 1.0D);
        endpoint.put("metadata", Collections.singletonMap("zone", "openapi-it"));
        
        Map<String, String> result = new LinkedHashMap<>();
        result.put("agentName", agentName);
        result.put("runtimeVersion", "1.0.0");
        result.put("versionRange", "[1.0.0]");
        result.put("protocol", "a2a");
        result.put("endpoints", JacksonUtils.toJson(List.of(endpoint)));
        return result;
    }
    
    private Query identityForm(String agentName) {
        return Query.newInstance().addParam("agentName", agentName)
                .addParam("protocol", "a2a");
    }
}
