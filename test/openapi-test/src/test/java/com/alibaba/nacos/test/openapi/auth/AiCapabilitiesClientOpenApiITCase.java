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

package com.alibaba.nacos.test.openapi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * External Client capability/auth contract with real identities.
 *
 * <p>Scenario coverage: all five binding features, identity without resource grants,
 * missing/invalid/blank credentials, resource isolation, lifecycle-free GET, and
 * unsupported methods. Client auth-off is validated by the directed deployment fixture.</p>
 *
 * @author Nacos
 */
public class AiCapabilitiesClientOpenApiITCase extends AuthITCase {

    private static final String PATH = CONTEXT_PATH + "/v3/client/ai/capabilities";

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CLIENT", "READONLY", "NO_PERMISSION"})
    void shouldReturnSameHttpCapabilitiesForEveryValidIdentity(String role) throws Exception {
        String token = login(System.getenv("NACOS_TEST_AUTH_" + role + "_USERNAME"),
            System.getenv("NACOS_TEST_AUTH_" + role + "_PASSWORD"));
        JsonNode data = assertSuccess(get(SERVER_BASE_URL, PATH, token)).get("data");
        assertEquals(1, data.get("schemaVersion").asInt());
        Set<String> fields = new HashSet<>();
        data.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("schemaVersion", "capabilities"), fields);
        JsonNode features = data.get("capabilities");
        fields.clear();
        features.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("radV1", "mcp", "skill", "prompt", "agentSpec"), fields);
        for (JsonNode feature : features) {
            assertTrue(feature.isBoolean());
            assertTrue(feature.booleanValue());
        }
    }

    @Test
    void shouldRejectAnonymousInvalidAndBlankEvenWithAnonymousAiEnabled() throws Exception {
        assertDenied(get(SERVER_BASE_URL, PATH, null));
        assertDenied(get(SERVER_BASE_URL, PATH, "invalid-token"));
        assertDenied(getWithAuthorization(SERVER_BASE_URL, PATH, ""));
        assertDenied(getWithAuthorization(SERVER_BASE_URL, PATH, "Bearer invalid-token"));
    }

    @Test
    void shouldRejectValidlySignedExpiredCredential() throws Exception {
        String secret = System.getenv("NACOS_TEST_AUTH_TOKEN_SECRET");
        assertTrue(secret != null && !secret.isBlank(), "The auth fixture must provide its test key");
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS256\"}"
            .getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"nacos_it_client_rw\",\"exp\":"
            + (System.currentTimeMillis() / 1000 - 60) + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
        String signature = encoder.encodeToString(mac.doFinal((header + '.' + payload)
            .getBytes(StandardCharsets.US_ASCII)));
        assertDenied(get(SERVER_BASE_URL, PATH, header + '.' + payload + '.' + signature));
    }

    @Test
    void shouldRejectReplacedPasswordThroughTheStandardIdentityFlow() throws Exception {
        String username = "capability-credential-" + UUID.randomUUID().toString().substring(0, 8);
        String oldPassword = "Nacos-Capability-Old-A1!";
        String newPassword = "Nacos-Capability-New-A2!";
        String userPath = CONTEXT_PATH + "/v3/auth/user";
        assertSuccess(postForm(SERVER_BASE_URL, userPath, adminToken(),
            params("username", username, "password", oldPassword)));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, userPath, adminToken(),
            params("username", username)));
        assertSuccess(awaitSuccess(() -> get(SERVER_BASE_URL,
            PATH + "?username=" + username + "&password=" + oldPassword, null)));
        assertSuccess(putForm(SERVER_BASE_URL, userPath, adminToken(),
            params("username", username, "newPassword", newPassword)));
        assertSuccess(awaitSuccess(() -> get(SERVER_BASE_URL,
            PATH + "?username=" + username + "&password=" + newPassword, null)));
        assertDenied(get(SERVER_BASE_URL,
            PATH + "?username=" + username + "&password=" + oldPassword, null));
    }

    @Test
    void shouldIgnoreResourceInputsWithoutGrantingBusinessAccess() throws Exception {
        String token = noPermissionToken();
        JsonNode baseline = assertSuccess(get(SERVER_BASE_URL, PATH, token));
        JsonNode withResources = assertSuccess(get(SERVER_BASE_URL,
            PATH + "?namespaceId=private&agentName=secret&mcpName=secret&version=1.0.0", token));
        assertEquals(baseline, withResources);
        assertDenied(get(SERVER_BASE_URL,
            CONTEXT_PATH + "/v3/client/ai/agents/search?namespaceId=public", token));
    }

    @Test
    void shouldNotCreateHttpClientWhenClientIdIsSupplied() throws Exception {
        String clientId = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            HttpRequest heartbeat = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL
                + CONTEXT_PATH + "/v3/client/ai/agents/endpoints/heartbeat"))
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Nacos-Client-Id", clientId)
                .header("Request-Module", "ai").PUT(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = httpClient.send(heartbeat,
                HttpResponse.BodyHandlers.ofString());
            assertEquals(50404, JacksonUtils.toObj(response.body()).get("code").asInt(),
                response.body());
            HttpRequest capability = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL + PATH))
                .header("Authorization", "Bearer " + noPermissionToken())
                .header("X-Nacos-Client-Id", clientId).GET().build();
            HttpResponse<String> result = httpClient.send(capability,
                HttpResponse.BodyHandlers.ofString());
            assertSuccess(new Response(result.statusCode(), result.body()));
        }
    }

    @Test
    void shouldNotKeepPublicationAliveByRepeatedCapabilityReads() throws Exception {
        String clientId = UUID.randomUUID().toString();
        String name = "capability-expiry-" + clientId;
        String endpointPath = CONTEXT_PATH + "/v3/client/ai/agents/endpoints";
        String identity = "namespaceId=public&agentName=" + name + "&protocol=a2a";
        String endpoints = URLEncoder.encode(
            "[{\"uri\":\"https://capability.example:8443\",\"transport\":\"JSONRPC\"}]",
            StandardCharsets.UTF_8);
        try {
            JsonNode liveness = assertSuccess(statefulRequest("POST", endpointPath, clientId,
                identity + "&runtimeVersion=1.0.0&versionRange=%5B1.0.0%5D&endpoints="
                    + endpoints)).get("data");
            long expiration = liveness.get("expireTimeoutMillis").asLong();
            assertTrue(expiration > 0 && expiration <= 60000, "Use bounded standalone intervals");
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(expiration + 15000);
            do {
                assertSuccess(statefulRequest("GET", PATH, clientId, null));
                Thread.sleep(500L);
            } while (System.nanoTime() < deadline);
            Response response = statefulRequest("PUT", endpointPath + "/heartbeat", clientId, null);
            assertEquals(50404, JacksonUtils.toObj(response.body()).get("code").asInt(),
                response.body());
        } finally {
            assertSuccess(statefulRequest("DELETE", endpointPath + '?' + identity, clientId, null));
        }
    }

    private Response statefulRequest(String method, String path, String clientId, String body)
        throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL + path))
            .header("Authorization", "Bearer " + adminToken())
            .header("X-Nacos-Client-Id", clientId).header("Request-Module", "ai")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void shouldRejectUnsupportedMethods(String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL + PATH))
            .header("Authorization", "Bearer " + adminToken())
            .method(method, HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode(), response.body());
        assertTrue(!response.body().contains("radV1"), response.body());
    }

    @Test
    void shouldUseStandardMvcHeadAndOptionsHandling() throws Exception {
        HttpRequest head = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL + PATH))
            .header("Authorization", "Bearer " + noPermissionToken())
            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> result = httpClient.send(head, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode());
        assertTrue(result.body().isEmpty());
        HttpRequest anonymousHead = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL + PATH))
            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403, httpClient.send(anonymousHead, HttpResponse.BodyHandlers.ofString())
            .statusCode());
        HttpRequest options = HttpRequest.newBuilder(URI.create(SERVER_BASE_URL + PATH))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        result = httpClient.send(options, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode());
        assertTrue(result.headers().firstValue("Allow").orElse("").contains("GET"));
        assertTrue(!result.headers().firstValue("Allow").orElse("").contains("POST"));
        assertTrue(result.body().isEmpty());
    }
}
