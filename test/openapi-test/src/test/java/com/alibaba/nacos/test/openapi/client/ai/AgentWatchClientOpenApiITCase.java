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

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@code POST /nacos/v3/client/ai/agents/watch}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: unchanged fingerprints long poll until timeout, changed items
 *     return immediately as opaque ids, a multi-intent batch returns only changed ids, and a
 *     Runtime Endpoint update wakes a custom-namespace request before Discover returns the new
 *     complete snapshot, and self-describing generations converge when consecutive requests
 *     reach different cluster nodes.</li>
 *     <li>Boundary/validation: mandatory stateful headers, generation and timeout bounds,
 *     malformed, empty, duplicate, mixed-namespace, malformed-fingerprint and 1001-item lists,
 *     and the independent request-byte hard limit are verified.</li>
 *     <li>Exception/error handling: the configured per-client soft watermark admits whole-set
 *     crossing but rejects later growth atomically; CI also lowers the per-node active-waiter
 *     hard limit so rejection, generation replacement, client-cancellation cleanup, and slot
 *     reuse are externally verified.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentWatchClientOpenApiITCase extends AgentClientOpenApiBaseITCase {

    private static final String REQUEST_MODULE = "AI";

    private static final String VERSION = "1.0.0";

    private static final String SERVER_WATCH_CAPACITY_PROPERTY =
            "nacos.agent.it.server.watch.capacity";

    private static final String SERVER_HTTP_WAITER_CAPACITY_PROPERTY =
            "nacos.agent.it.server.http.watch.waiter.capacity";

    private static final String PEER_PORT_PROPERTY = "nacos.agent.it.peer.port";

    private static final String DIFFERENT_FINGERPRINT =
            AgentDiscoveryCanonicalizer.ALGORITHM_ID + ":" + "0".repeat(64);

    @Test
    public void testTimeoutAndMultiIntentChangedResponsesAreOpaque() throws Exception {
        String firstAgent = randomAiName("agent-watch-first");
        String secondAgent = randomAiName("agent-watch-second");
        publishAgent(firstAgent, VERSION);
        publishAgent(secondAgent, VERSION);

        JsonNode first = discover(null, firstAgent);
        JsonNode second = discover(null, secondAgent);
        String firstFingerprint = fingerprint(first);
        String secondFingerprint = fingerprint(second);
        String clientId = randomHttpClientId();

        long startedNanos = System.nanoTime();
        JsonNode timedOut = assertWatchResponse(postWatchForm(clientId, REQUEST_MODULE,
                watchForm(1L, 1000L, Collections.singletonList(
                        watchItem("first", null, firstAgent, firstFingerprint)))),
                1L, false, Collections.emptyList());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        assertTrue(elapsedMillis >= 700L, "unchanged Watch returned too early: " + elapsedMillis);
        assertFalse(timedOut.has("changedClientWatchIds"), timedOut.toString());

        List<Map<String, Object>> batch = new ArrayList<>();
        batch.add(watchItem("first", null, firstAgent, firstFingerprint));
        batch.add(watchItem("second", DEFAULT_NAMESPACE, secondAgent,
                DIFFERENT_FINGERPRINT));
        JsonNode changed = assertWatchResponse(postWatchForm(clientId, REQUEST_MODULE,
                watchForm(2L, 5000L, batch)), 2L, true,
                Collections.singletonList("second"));
        assertOpaqueInvalidation(changed);

        JsonNode current = discover(null, secondAgent);
        assertEquals(secondFingerprint, fingerprint(current), current.toString());
        assertEquals(secondAgent, current.get("agentName").asText(), current.toString());
        assertTrue(current.get("callInterfaces").isArray(), current.toString());
    }

    @Test
    public void testRuntimeEndpointChangeWakesCustomNamespaceWatch() throws Exception {
        String namespaceId = randomAiName("agent-watch-namespace");
        String agentName = randomAiName("agent-watch-runtime");
        publishCustomAgent(namespaceId, agentName);
        JsonNode before = discover(namespaceId, agentName);
        String beforeFingerprint = fingerprint(before);
        String watchClientId = randomHttpClientId();
        Map<String, Object> watch = watchItem("runtime", namespaceId, agentName,
                beforeFingerprint);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<HttpResponse> pending = executor.submit(() -> postWatchForm(watchClientId,
                REQUEST_MODULE, watchForm(2L, 60000L, Collections.singletonList(watch))));
        try {
            awaitActiveGeneration(watchClientId, 2L, watch);

            String publisherClientId = randomHttpClientId();
            addCleanup(() -> deleteEndpointForm(publisherClientId, REQUEST_MODULE,
                    endpointIdentity(namespaceId, agentName)));
            assertEquals(200, postEndpointForm(publisherClientId, REQUEST_MODULE,
                    endpointRegistration(namespaceId, agentName)).code());

            assertWatchResponse(pending.get(10L, TimeUnit.SECONDS), 2L, true,
                    Collections.singletonList("runtime"));
            JsonNode after = awaitFingerprintChange(namespaceId, agentName, beforeFingerprint);
            assertEquals(namespaceId, after.get("namespaceId").asText(), after.toString());
            assertEquals(agentName, after.get("agentName").asText(), after.toString());
            assertTrue(runtimeEndpointCount(after) > 0, after.toString());
        } finally {
            pending.cancel(true);
            executor.shutdownNow();
        }
    }

    @Test
    public void testValidationAndRequestByteCapacity() throws Exception {
        Map<String, Object> validItem = watchItem("watch", null,
                randomAiName("agent-watch-validation"), DIFFERENT_FINGERPRINT);
        Map<String, String> valid = watchForm(1L, 1000L,
                Collections.singletonList(validItem));
        assertError(postWatchForm(null, REQUEST_MODULE, valid), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "X-Nacos-Client-Id");
        assertError(postWatchForm(randomHttpClientId(), null, valid), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Request-Module");

        assertValidation(valid, "generation", "-1", "generation");
        assertValidation(valid, "timeoutMillis", "999", "timeoutMillis");
        assertValidation(valid, "timeoutMillis", "60001", "timeoutMillis");
        assertValidation(valid, "watches", "[]", "at least one item");
        assertValidation(valid, "watches", "{", "not valid JSON");
        assertValidation(valid, "watches", "[null]", "null items");

        List<Map<String, Object>> duplicates = new ArrayList<>();
        duplicates.add(validItem);
        duplicates.add(watchItem("watch", null, randomAiName("agent-watch-duplicate"),
                DIFFERENT_FINGERPRINT));
        assertValidation(valid, "watches", JacksonUtils.toJson(duplicates), "duplicate");

        List<Map<String, Object>> mixedNamespaces = new ArrayList<>();
        mixedNamespaces.add(validItem);
        mixedNamespaces.add(watchItem("other", "team", randomAiName("agent-watch-mixed"),
                DIFFERENT_FINGERPRINT));
        assertValidation(valid, "watches", JacksonUtils.toJson(mixedNamespaces),
                "one effective namespace");

        Map<String, Object> malformedFingerprint = new LinkedHashMap<>(validItem);
        malformedFingerprint.put("materializedFingerprint", "bad");
        assertValidation(valid, "watches",
                JacksonUtils.toJson(Collections.singletonList(malformedFingerprint)),
                "materializedFingerprint");

        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= 1000; i++) {
            tooMany.add(watchItem("watch-" + i, null, "agent-" + i,
                    DIFFERENT_FINGERPRINT));
        }
        assertValidation(valid, "watches", JacksonUtils.toJson(tooMany), "at most 1000");

        Map<String, String> selector = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            selector.put("key-" + i, "x".repeat(256));
        }
        List<Map<String, Object>> oversizedItems = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> item = watchItem("large-" + i, null,
                    "large-agent", DIFFERENT_FINGERPRINT);
            @SuppressWarnings("unchecked")
            Map<String, Object> discoveryRequest =
                    (Map<String, Object>) item.get("discoveryRequest");
            discoveryRequest.put("filter",
                    Collections.singletonMap("metadataSelector", selector));
            oversizedItems.add(item);
        }
        String oversizedJson = JacksonUtils.toJson(oversizedItems);
        assertTrue(oversizedJson.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024);
        Map<String, String> largeRequest = new LinkedHashMap<>();
        largeRequest.put("generation", "1");
        largeRequest.put("timeoutMillis", "1000");
        largeRequest.put("watches", oversizedJson);
        assertError(postWatchForm(randomHttpClientId(), REQUEST_MODULE, largeRequest), 503,
                ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, "request-byte limit");
    }

    @Test
    public void testConfiguredPerClientSoftCapacityAndGenerationReplacement() throws Exception {
        int capacity = Integer.getInteger(SERVER_WATCH_CAPACITY_PROPERTY, 300);
        String agentName = randomAiName("agent-watch-soft-capacity");
        publishAgent(agentName, VERSION);
        String fingerprint = fingerprint(discover(null, agentName));
        String clientId = randomHttpClientId();
        List<Map<String, Object>> crossing = repeatedItems(capacity + 1, agentName, fingerprint);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<HttpResponse> admitted = executor.submit(() -> postWatchForm(clientId,
                REQUEST_MODULE, watchForm(2L, 60000L, crossing)));
        try {
            awaitActiveGeneration(clientId, 2L, crossing.get(0));

            List<Map<String, Object>> growth = repeatedItems(capacity + 2, agentName,
                    fingerprint);
            assertError(postWatchForm(clientId, REQUEST_MODULE,
                    watchForm(3L, 1000L, growth)), 503,
                    ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT,
                    "limit of " + capacity);

            List<Map<String, Object>> replacement = new ArrayList<>(crossing);
            replacement.set(0, watchItem("watch-0", null, agentName,
                    DIFFERENT_FINGERPRINT));
            assertWatchResponse(postWatchForm(clientId, REQUEST_MODULE,
                    watchForm(4L, 1000L, replacement)), 4L, true,
                    Collections.singletonList("watch-0"));
            assertWatchResponse(admitted.get(5L, TimeUnit.SECONDS), 2L, false,
                    Collections.emptyList());
        } finally {
            admitted.cancel(true);
            executor.shutdownNow();
        }
    }

    @Test
    public void testConfiguredNodeWaiterCapacityAndSlotReuse() throws Exception {
        int capacity = Integer.getInteger(SERVER_HTTP_WAITER_CAPACITY_PROPERTY, 0);
        Assumptions.assumeTrue(capacity > 0,
                "Node waiter capacity IT requires matching standalone-server configuration");
        String agentName = randomAiName("agent-watch-node-capacity");
        publishAgent(agentName, VERSION);
        String fingerprint = fingerprint(discover(null, agentName));
        Map<String, Object> current = watchItem("watch", null, agentName, fingerprint);
        ExecutorService executor = Executors.newFixedThreadPool(capacity);
        List<String> clientIds = new ArrayList<>();
        List<Future<HttpResponse>> admitted = new ArrayList<>();
        try {
            for (int i = 0; i < capacity; i++) {
                String clientId = randomHttpClientId();
                clientIds.add(clientId);
                admitted.add(executor.submit(() -> postWatchForm(clientId, REQUEST_MODULE,
                        watchForm(2L, 60000L, Collections.singletonList(current)))));
                awaitActiveGeneration(clientId, 2L, current);
            }

            assertError(postWatchForm(randomHttpClientId(), REQUEST_MODULE,
                    watchForm(2L, 1000L, Collections.singletonList(current))), 503,
                    ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT,
                    "waiter capacity");

            for (String clientId : clientIds) {
                Map<String, Object> changed = watchItem("watch", null, agentName,
                        DIFFERENT_FINGERPRINT);
                assertWatchResponse(postWatchForm(clientId, REQUEST_MODULE,
                        watchForm(3L, 1000L, Collections.singletonList(changed))),
                        3L, true, Collections.singletonList("watch"));
            }
            for (Future<HttpResponse> each : admitted) {
                assertWatchResponse(each.get(5L, TimeUnit.SECONDS), 2L, false,
                        Collections.emptyList());
            }

            Map<String, Object> changed = watchItem("watch", null, agentName,
                    DIFFERENT_FINGERPRINT);
            assertWatchResponse(postWatchForm(randomHttpClientId(), REQUEST_MODULE,
                    watchForm(1L, 1000L, Collections.singletonList(changed))),
                    1L, true, Collections.singletonList("watch"));
        } finally {
            for (Future<HttpResponse> each : admitted) {
                each.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    @Test
    public void testCancelledClientRequestReleasesCapacityWithinTimeout() throws Exception {
        int capacity = Integer.getInteger(SERVER_HTTP_WAITER_CAPACITY_PROPERTY, 0);
        Assumptions.assumeTrue(capacity > 0,
                "Cancellation IT requires matching standalone-server waiter capacity");
        String agentName = randomAiName("agent-watch-cancel");
        publishAgent(agentName, VERSION);
        String fingerprint = fingerprint(discover(null, agentName));
        Map<String, Object> current = watchItem("watch", null, agentName, fingerprint);
        String cancelledClientId = randomHttpClientId();
        HttpPost cancelledRequest = watchRequest(requestUrl(AGENT_WATCH_PATH),
                cancelledClientId, REQUEST_MODULE,
                watchForm(2L, 1000L, Collections.singletonList(current)));
        ExecutorService executor = Executors.newFixedThreadPool(capacity);
        Future<HttpResponse> cancelled = executor.submit(() -> executeRaw(cancelledRequest));
        List<String> retainedClientIds = new ArrayList<>();
        List<Future<HttpResponse>> retained = new ArrayList<>();
        try {
            awaitActiveGeneration(cancelledClientId, 2L, current);
            for (int i = 1; i < capacity; i++) {
                String clientId = randomHttpClientId();
                retainedClientIds.add(clientId);
                retained.add(executor.submit(() -> postWatchForm(clientId, REQUEST_MODULE,
                        watchForm(2L, 60000L, Collections.singletonList(current)))));
                awaitActiveGeneration(clientId, 2L, current);
            }

            cancelledRequest.cancel();
            cancelled.cancel(true);
            Map<String, Object> changed = watchItem("watch", null, agentName,
                    DIFFERENT_FINGERPRINT);
            HttpResponse admitted = awaitNodeCapacityReuse(changed);
            assertWatchResponse(admitted, 1L, true, Collections.singletonList("watch"));
        } finally {
            Map<String, Object> changed = watchItem("watch", null, agentName,
                    DIFFERENT_FINGERPRINT);
            for (String clientId : retainedClientIds) {
                assertWatchResponse(postWatchForm(clientId, REQUEST_MODULE,
                        watchForm(3L, 1000L, Collections.singletonList(changed))),
                        3L, true, Collections.singletonList("watch"));
            }
            for (Future<HttpResponse> each : retained) {
                assertWatchResponse(each.get(5L, TimeUnit.SECONDS), 2L, false,
                        Collections.emptyList());
            }
            cancelled.cancel(true);
            for (Future<HttpResponse> each : retained) {
                each.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    @Test
    public void testCrossNodeSelfDescribingGenerationsConverge() throws Exception {
        String peerBaseUrl = "http://" + NACOS_HOST + ":"
                + System.getProperty(PEER_PORT_PROPERTY, NACOS_PORT);
        String agentName = randomAiName("agent-watch-cross-node");
        publishAgent(agentName, VERSION);
        JsonNode before = awaitDiscover(peerBaseUrl, agentName);
        String beforeFingerprint = fingerprint(before);
        String watchClientId = randomHttpClientId();
        Map<String, Object> watch = watchItem("cross-node", null, agentName,
                beforeFingerprint);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<HttpResponse> generationTen = executor.submit(() -> postWatchForm(
                watchClientId, REQUEST_MODULE,
                watchForm(10L, 2000L, Collections.singletonList(watch))));
        String publisherClientId = randomHttpClientId();
        try {
            awaitActiveGeneration(watchClientId, 10L, watch);
            addCleanup(() -> deleteEndpointAt(peerBaseUrl, publisherClientId,
                    endpointIdentity(DEFAULT_NAMESPACE, agentName)));
            assertEquals(200, postEndpointAt(peerBaseUrl, publisherClientId,
                    endpointRegistration(DEFAULT_NAMESPACE, agentName)).code());

            JsonNode generationTenData = assertWatchEnvelope(
                    generationTen.get(5L, TimeUnit.SECONDS), 10L);
            if (generationTenData.get("changed").asBoolean()) {
                assertEquals(Collections.singletonList("cross-node"),
                        changedIds(generationTenData), generationTenData.toString());
            }

            long startedNanos = System.nanoTime();
            JsonNode generationEleven = assertWatchResponse(postWatchAt(peerBaseUrl,
                    watchClientId, REQUEST_MODULE,
                    watchForm(11L, 5000L, Collections.singletonList(watch))),
                    11L, true, Collections.singletonList("cross-node"));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedNanos);
            assertTrue(elapsedMillis < 3000L,
                    "self-describing peer generation did not converge quickly: "
                            + elapsedMillis);
            assertOpaqueInvalidation(generationEleven);

            JsonNode after = awaitPeerFingerprintChange(peerBaseUrl, agentName,
                    beforeFingerprint);
            JsonNode localAfter = awaitPeerFingerprintChange(baseUrl(), agentName,
                    beforeFingerprint);
            String afterFingerprint = fingerprint(localAfter);
            assertEquals(afterFingerprint, fingerprint(after), after.toString());
            JsonNode generationTwelve = assertWatchResponse(postWatchForm(watchClientId,
                    REQUEST_MODULE, watchForm(12L, 1000L, Collections.singletonList(
                            watchItem("cross-node", null, agentName, afterFingerprint)))),
                    12L, false, Collections.emptyList());
            assertFalse(generationTwelve.has("changedClientWatchIds"),
                    generationTwelve.toString());
        } finally {
            generationTen.cancel(true);
            executor.shutdownNow();
        }
    }

    private void publishCustomAgent(String namespaceId, String agentName) throws Exception {
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(namespaceId, agentName, VERSION)));
        addCleanup(() -> deleteAgentDefinitionQuietly(namespaceId, agentName));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(namespaceId, agentName, VERSION)));
    }

    private JsonNode discover(String namespaceId, String agentName) throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName);
        if (null != namespaceId) {
            query.addParam("namespaceId", namespaceId);
        }
        HttpResponse response = getWithClientId(AGENT_CLIENT_PATH, query, null);
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root.get("data");
    }

    private String fingerprint(JsonNode discovery) {
        AgentDiscoveryResult result = JacksonUtils.toObj(discovery.toString(),
                AgentDiscoveryResult.class);
        return AgentDiscoveryCanonicalizer.fingerprint(result);
    }

    private Map<String, String> watchForm(long generation, long timeoutMillis,
            List<Map<String, Object>> watches) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("generation", Long.toString(generation));
        result.put("timeoutMillis", Long.toString(timeoutMillis));
        result.put("watches", JacksonUtils.toJson(watches));
        return result;
    }

    private Map<String, Object> watchItem(String clientWatchId, String namespaceId,
            String agentName, String materializedFingerprint) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("agentName", agentName);
        Map<String, Object> discoveryRequest = new LinkedHashMap<>();
        if (null != namespaceId) {
            discoveryRequest.put("namespaceId", namespaceId);
        }
        discoveryRequest.put("reference", reference);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientWatchId", clientWatchId);
        result.put("discoveryRequest", discoveryRequest);
        result.put("materializedFingerprint", materializedFingerprint);
        return result;
    }

    private List<Map<String, Object>> repeatedItems(int count, String agentName,
            String materializedFingerprint) {
        List<Map<String, Object>> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(watchItem("watch-" + i, null, agentName, materializedFingerprint));
        }
        return result;
    }

    private JsonNode assertWatchResponse(HttpResponse response, long generation,
            boolean changed, List<String> expectedIds) throws Exception {
        JsonNode data = assertWatchEnvelope(response, generation);
        assertEquals(changed, data.get("changed").asBoolean(), data.toString());
        assertEquals(expectedIds, changedIds(data), data.toString());
        return data;
    }

    private JsonNode assertWatchEnvelope(HttpResponse response, long generation)
            throws Exception {
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        JsonNode data = root.get("data");
        assertEquals(generation, data.get("generation").asLong(), data.toString());
        return data;
    }

    private List<String> changedIds(JsonNode data) {
        List<String> result = new ArrayList<>();
        if (data.has("changedClientWatchIds")) {
            data.get("changedClientWatchIds").forEach(each -> result.add(each.asText()));
        }
        return result;
    }

    private void assertOpaqueInvalidation(JsonNode data) {
        assertFalse(data.has("fingerprint"), data.toString());
        assertFalse(data.has("materializedFingerprint"), data.toString());
        assertFalse(data.has("agentName"), data.toString());
        assertFalse(data.has("callInterfaces"), data.toString());
        assertFalse(data.has("endpoints"), data.toString());
        assertFalse(data.has("error"), data.toString());
    }

    private void assertValidation(Map<String, String> valid, String field, String value,
            String expectedMessage) throws Exception {
        Map<String, String> invalid = new LinkedHashMap<>(valid);
        invalid.put(field, value);
        assertError(postWatchForm(randomHttpClientId(), REQUEST_MODULE, invalid), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, expectedMessage);
    }

    private void awaitActiveGeneration(String clientId, long activeGeneration,
            Map<String, Object> currentItem) throws Exception {
        Map<String, Object> probe = new LinkedHashMap<>(currentItem);
        probe.put("materializedFingerprint", DIFFERENT_FINGERPRINT);
        for (int retry = 0; retry < 20; retry++) {
            HttpResponse response = postWatchForm(clientId, REQUEST_MODULE,
                    watchForm(activeGeneration - 1L, 1000L,
                            Collections.singletonList(probe)));
            assertEquals(200, response.code(), response.body());
            JsonNode root = JacksonUtils.toObj(response.body());
            assertSuccess(root);
            JsonNode data = root.get("data");
            assertEquals(activeGeneration - 1L, data.get("generation").asLong(),
                    data.toString());
            if (!data.get("changed").asBoolean()) {
                return;
            }
            assertEquals(probe.get("clientWatchId").toString(),
                    data.get("changedClientWatchIds").get(0).asText(), data.toString());
            TimeUnit.MILLISECONDS.sleep(50L);
        }
        throw new AssertionError("Watch generation " + activeGeneration
                + " was not admitted for client " + clientId);
    }

    private Map<String, String> endpointRegistration(String namespaceId, String agentName) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("uri", "http://127.0.0.1:18080/agent-watch");
        endpoint.put("transport", "HTTP");
        endpoint.put("priority", 0);
        endpoint.put("weight", 1.0D);
        endpoint.put("metadata", Collections.singletonMap("scenario", "watch"));
        Map<String, String> result = new LinkedHashMap<>();
        result.put("namespaceId", namespaceId);
        result.put("agentName", agentName);
        result.put("runtimeVersion", VERSION);
        result.put("versionRange", "[" + VERSION + "]");
        result.put("protocol", "a2a");
        result.put("endpoints", JacksonUtils.toJson(Collections.singletonList(endpoint)));
        return result;
    }

    private Query endpointIdentity(String namespaceId, String agentName) {
        return Query.newInstance().addParam("namespaceId", namespaceId)
                .addParam("agentName", agentName).addParam("protocol", "a2a");
    }

    private JsonNode awaitFingerprintChange(String namespaceId, String agentName,
            String previousFingerprint) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry < 40; retry++) {
            last = discover(namespaceId, agentName);
            if (!previousFingerprint.equals(fingerprint(last))) {
                return last;
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        throw new AssertionError("Agent discovery fingerprint did not change: " + last);
    }

    private int runtimeEndpointCount(JsonNode discovery) {
        int result = 0;
        for (JsonNode callInterface : discovery.get("callInterfaces")) {
            for (JsonNode endpointSet : callInterface.get("endpointSets")) {
                if ("RUNTIME".equals(endpointSet.get("source").asText())) {
                    result += endpointSet.get("endpoints").size();
                }
            }
        }
        return result;
    }

    private HttpResponse awaitNodeCapacityReuse(Map<String, Object> changed) throws Exception {
        HttpResponse last = null;
        String clientId = randomHttpClientId();
        for (int retry = 0; retry < 60; retry++) {
            last = postWatchForm(clientId, REQUEST_MODULE,
                    watchForm(1L, 1000L, Collections.singletonList(changed)));
            if (200 == last.code()) {
                return last;
            }
            assertError(last, 503, ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT,
                    "waiter capacity");
            TimeUnit.MILLISECONDS.sleep(50L);
        }
        throw new AssertionError("Cancelled Watch did not release node capacity: " + last);
    }

    private JsonNode awaitDiscover(String serverBaseUrl, String agentName) throws Exception {
        HttpResponse last = null;
        for (int retry = 0; retry < 100; retry++) {
            last = discoverAt(serverBaseUrl, agentName);
            if (200 == last.code()) {
                JsonNode root = JacksonUtils.toObj(last.body());
                assertSuccess(root);
                return root.get("data");
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        throw new AssertionError("Agent did not converge to peer: " + last);
    }

    private HttpResponse discoverAt(String serverBaseUrl, String agentName) throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName);
        HttpGet request = new HttpGet(serverBaseUrl + AGENT_CLIENT_PATH + "?"
                + query.toQueryUrl());
        request.setHeader(ClientConstants.HTTP_CLIENT_ID_HEADER, randomHttpClientId());
        return executeRaw(request);
    }

    private HttpResponse postWatchAt(String serverBaseUrl, String clientId,
            String requestModule, Map<String, String> form) throws Exception {
        return executeRaw(watchRequest(serverBaseUrl + AGENT_WATCH_PATH, clientId,
                requestModule, form));
    }

    private HttpResponse postEndpointAt(String serverBaseUrl, String clientId,
            Map<String, String> form) throws Exception {
        HttpPost request = new HttpPost(serverBaseUrl + AGENT_ENDPOINT_PATH);
        addPeerStatefulHeaders(request, clientId);
        HttpUtils.initRequestFromEntity(request, form, StandardCharsets.UTF_8.name());
        return executeRaw(request);
    }

    private void deleteEndpointAt(String serverBaseUrl, String clientId, Query form)
            throws Exception {
        HttpDelete request = new HttpDelete(serverBaseUrl + AGENT_ENDPOINT_PATH + "?"
                + form.toQueryUrl());
        addPeerStatefulHeaders(request, clientId);
        executeRaw(request);
    }

    private void addPeerStatefulHeaders(org.apache.hc.core5.http.ClassicHttpRequest request,
            String clientId) {
        request.setHeader(ClientConstants.HTTP_CLIENT_ID_HEADER, clientId);
        request.setHeader(HttpHeaderConsts.REQUEST_MODULE, REQUEST_MODULE);
    }

    private JsonNode awaitPeerFingerprintChange(String serverBaseUrl, String agentName,
            String previousFingerprint) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry < 100; retry++) {
            HttpResponse response = discoverAt(serverBaseUrl, agentName);
            if (200 == response.code()) {
                JsonNode root = JacksonUtils.toObj(response.body());
                assertSuccess(root);
                last = root.get("data");
                if (!previousFingerprint.equals(fingerprint(last))) {
                    return last;
                }
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        throw new AssertionError("Peer Agent fingerprint did not change: " + last);
    }
}
