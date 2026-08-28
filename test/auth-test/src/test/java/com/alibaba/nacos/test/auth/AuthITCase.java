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

package com.alibaba.nacos.test.auth;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared auth-enabled standalone-server integration test support.
 *
 * @author Nacos
 */
abstract class AuthITCase {

    protected static final String NACOS_HOST =
            System.getProperty("nacos.host", "127.0.0.1");

    protected static final int NACOS_PORT =
            Integer.parseInt(System.getProperty("nacos.port", "8848"));

    protected static final int NACOS_CONSOLE_PORT =
            Integer.parseInt(System.getProperty("nacos.console.port", "8080"));

    protected static final String SERVER_BASE_URL =
            "http://" + NACOS_HOST + ':' + NACOS_PORT;

    protected static final String CONSOLE_BASE_URL =
            "http://" + NACOS_HOST + ':' + NACOS_CONSOLE_PORT;

    protected static final String CONTEXT_PATH = "/nacos";

    private static final String ADMIN_USERNAME =
            System.getProperty("nacos.auth.username", "nacos");

    private static final String ADMIN_PASSWORD =
            System.getProperty("nacos.auth.password", "NacosAuth123!");

    private static final String USER_PATH = CONTEXT_PATH + "/v3/auth/user";

    private static final String ROLE_PATH = CONTEXT_PATH + "/v3/auth/role";

    private static final String PERMISSION_PATH =
            CONTEXT_PATH + "/v3/auth/permission";

    private final ArrayDeque<CleanupAction> cleanupActions = new ArrayDeque<>();

    protected HttpClient httpClient;

    private String adminToken;

    @BeforeEach
    void setUpAuthClient() throws Exception {
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    @AfterEach
    void tearDownAuthData() throws Exception {
        Exception failure = null;
        while (!cleanupActions.isEmpty()) {
            try {
                cleanupActions.removeLast().run();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    protected String adminToken() {
        return adminToken;
    }

    protected TestIdentity createIdentityWithoutPermission(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = prefix + '-' + suffix;
        String password = "AuthTest123!";
        String role = "ROLE_" + prefix.toUpperCase().replace('-', '_') + '_' + suffix;

        assertSuccess(postForm(SERVER_BASE_URL, USER_PATH, adminToken,
                params("username", username, "password", password)));
        cleanupActions.add(() -> deleteForm(SERVER_BASE_URL, USER_PATH, adminToken,
                params("username", username)));

        assertSuccess(postForm(SERVER_BASE_URL, ROLE_PATH, adminToken,
                params("role", role, "username", username)));
        cleanupActions.add(() -> deleteForm(SERVER_BASE_URL, ROLE_PATH, adminToken,
                params("role", role, "username", username)));

        return new TestIdentity(username, role, awaitLogin(username, password));
    }

    protected void grantReadPermission(TestIdentity identity, String resource)
            throws Exception {
        grantPermission(identity, resource, "r");
    }

    protected void grantPermission(TestIdentity identity, String resource, String action)
            throws Exception {
        assertSuccess(postForm(SERVER_BASE_URL, PERMISSION_PATH, adminToken,
                params("role", identity.role(), "resource", resource, "action", action)));
        cleanupActions.add(() -> deleteForm(SERVER_BASE_URL, PERMISSION_PATH, adminToken,
                params("role", identity.role(), "resource", resource, "action", action)));
    }

    protected void addCleanup(CleanupAction action) {
        cleanupActions.add(action);
    }

    protected String login(String username, String password) throws Exception {
        Response response = postForm(SERVER_BASE_URL, USER_PATH + "/login", null,
                params("username", username, "password", password));
        assertEquals(200, response.status(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertNotNull(root, response.body());
        assertTrue(root.hasNonNull("accessToken"), response.body());
        return root.get("accessToken").asText();
    }

    protected String awaitLogin(String username, String password) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        AssertionError lastFailure = null;
        do {
            try {
                return login(username, password);
            } catch (AssertionError e) {
                lastFailure = e;
                Thread.sleep(250L);
            }
        } while (System.nanoTime() < deadline);
        throw lastFailure;
    }

    protected Response get(String baseUrl, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15)).GET();
        addToken(builder, token);
        return execute(builder.build());
    }

    protected Response rawGet(String requestTarget) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(NACOS_HOST, NACOS_PORT), 5000);
            socket.setSoTimeout(15000);
            String request = "GET " + requestTarget + " HTTP/1.1\r\nHost: " + NACOS_HOST + ':'
                    + NACOS_PORT + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            if (response.isEmpty()) {
                return new Response(0, "Connection closed without an HTTP response");
            }
            int firstSpace = response.indexOf(' ');
            int secondSpace = response.indexOf(' ', firstSpace + 1);
            if (firstSpace < 0 || secondSpace < 0) {
                return new Response(0, response);
            }
            return new Response(Integer.parseInt(response.substring(firstSpace + 1, secondSpace)),
                    response);
        }
    }

    protected Response postForm(String baseUrl, String path, String token,
            Map<String, String> parameters) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeParameters(parameters)));
        addToken(builder, token);
        return execute(builder.build());
    }

    protected Response putForm(String baseUrl, String path, String token,
            Map<String, String> parameters) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .PUT(HttpRequest.BodyPublishers.ofString(encodeParameters(parameters)));
        addToken(builder, token);
        return execute(builder.build());
    }

    protected Response deleteForm(String baseUrl, String path, String token,
            Map<String, String> parameters) throws Exception {
        String requestPath = path + '?' + encodeParameters(parameters);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + requestPath))
                .timeout(Duration.ofSeconds(15)).DELETE();
        addToken(builder, token);
        return execute(builder.build());
    }

    protected Response request(RequestMethod method, String baseUrl, String path, String token)
            throws Exception {
        return switch (method) {
            case GET -> get(baseUrl, path, token);
            case POST -> postForm(baseUrl, path, token, Map.of());
            case PUT -> putForm(baseUrl, path, token, Map.of());
            case DELETE -> deleteForm(baseUrl, path, token, Map.of());
        };
    }

    protected void assertDenied(Response response) {
        assertEquals(403, response.status(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertEquals(10001, root.get("code").asInt(), response.body());
    }

    protected void assertBlocked(Response response) {
        assertTrue(response.status() == 0
                        || response.status() >= 400 && response.status() < 600,
                "Suspicious URI must not return business data or redirect: "
                        + response.status() + ": " + response.body());
    }

    protected JsonNode assertSuccess(Response response) {
        assertEquals(200, response.status(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertNotNull(root, response.body());
        assertEquals(0, root.get("code").asInt(), response.body());
        return root;
    }

    protected static Map<String, String> params(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private Response execute(HttpRequest request) throws Exception {
        java.net.http.HttpResponse<String> response =
                httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private void addToken(HttpRequest.Builder builder, String token) {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private String encodeParameters(Map<String, String> parameters) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!result.isEmpty()) {
                result.append('&');
            }
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    protected record Response(int status, String body) {
    }

    protected record TestIdentity(String username, String role, String token) {
    }

    protected enum RequestMethod {
        GET,
        POST,
        PUT,
        DELETE
    }

    @FunctionalInterface
    protected interface CleanupAction {

        void run() throws Exception;
    }
}
