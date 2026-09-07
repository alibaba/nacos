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

package com.alibaba.nacos.test.sdk;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.NacosLockFactory;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared standalone-server Java SDK integration test infrastructure.
 *
 * @author xiweng.yy
 */
public abstract class JavaSdkBaseITCase {
    
    protected static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    protected static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    protected static final String SERVER_ADDR = System.getProperty("nacos.server.address",
            NACOS_HOST + ":" + NACOS_PORT);

    protected static final String SERVER_HTTP_BASE_URL = System.getProperty(
            "nacos.server.http.base-url", "http://" + NACOS_HOST + ":" + NACOS_PORT + "/nacos");

    protected static final String CONSOLE_BASE_URL = System.getProperty(
            "nacos.console.base-url", "http://" + NACOS_HOST + ":"
                    + System.getProperty("nacos.console.port", "8080"));

    protected static final boolean AUTH_ENABLED = Boolean.parseBoolean(
            System.getProperty("nacos.test.auth.enabled", "false"));

    protected static final boolean ANONYMOUS_AI_ENABLED = Boolean.parseBoolean(
            System.getProperty("nacos.test.auth.anonymous-ai.enabled", "false"));
    
    protected static final int DEFAULT_TIMEOUT_MS = 3000;
    
    private static final String SDK_STATUS_UP = "UP";

    private static final String AI_CONNECTION_PROBE = "java-sdk-it-ai-connection-probe";

    private static final String AUTH_LOGIN_PATH = "/v3/auth/user/login";

    private static final String AUTH_VISIBILITY_PATH = "/v3/auth/visibility";

    private static final int TEST_ENDPOINT_PORT_MIN = 10000;

    private static final int TEST_ENDPOINT_PORT_RANGE = 30000;

    private static final AtomicInteger NEXT_TEST_ENDPOINT_PORT = new AtomicInteger(
            Math.floorMod(UUID.randomUUID().hashCode(), TEST_ENDPOINT_PORT_RANGE));
    
    private final Deque<CleanupAction> cleanupActions = new ArrayDeque<>();
    
    private final Deque<CleanupAction> shutdownActions = new ArrayDeque<>();

    private String adminAccessToken;
    
    @AfterEach
    public void tearDownJavaSdkBase() throws Exception {
        Exception failure = runActions(cleanupActions, null);
        failure = runActions(shutdownActions, failure);
        if (null != failure) {
            throw failure;
        }
    }
    
    protected ConfigService createConfigService() throws Exception {
        return createConfigService(sdkProperties());
    }

    protected ConfigService createConfigService(Properties properties) throws Exception {
        ConfigService service = createConfigServiceWithoutReadiness(properties);
        waitUntil("config SDK client should connect to server",
                () -> SDK_STATUS_UP.equals(service.getServerStatus()));
        return service;
    }

    protected ConfigService createConfigServiceWithoutReadiness(Properties properties)
            throws NacosException {
        ConfigService service = ConfigFactory.createConfigService(properties);
        shutdownActions.addFirst(service::shutDown);
        return service;
    }
    
    protected NamingService createNamingService() throws Exception {
        return createNamingService(sdkProperties());
    }

    protected NamingService createNamingService(Properties properties) throws Exception {
        NamingService service = createNamingServiceWithoutReadiness(properties);
        waitUntil("naming SDK client should connect to server",
                () -> SDK_STATUS_UP.equals(service.getServerStatus()));
        return service;
    }

    protected NamingService createNamingServiceWithoutReadiness(Properties properties)
            throws NacosException {
        NamingService service = NamingFactory.createNamingService(properties);
        shutdownActions.addFirst(service::shutDown);
        return service;
    }
    
    protected AiService createAiService() throws Exception {
        return createAiService(sdkProperties());
    }

    protected AiService createAiService(Properties properties) throws Exception {
        AiService service = createAiServiceWithoutReadiness(properties);
        AgentSearchRequest probe = new AgentSearchRequest();
        probe.setAgentNameContains(AI_CONNECTION_PROBE);
        probe.setPageNo(1);
        probe.setPageSize(1);
        waitUntil("AI SDK client should connect to server", () -> {
            service.searchAgents(probe);
            return true;
        });
        return service;
    }

    protected AiService createAiServiceWithoutReadiness(Properties properties)
            throws NacosException {
        AiService service = AiFactory.createAiService(properties);
        shutdownActions.addFirst(service::shutdown);
        return service;
    }
    
    protected LockService createLockService() throws NacosException {
        return createLockService(sdkProperties());
    }

    protected LockService createLockService(Properties properties) throws NacosException {
        LockService service = NacosLockFactory.createLockService(properties);
        shutdownActions.addFirst(service::shutdown);
        return service;
    }
    
    protected Properties sdkProperties() {
        return sdkProperties(AUTH_ENABLED ? AuthIdentity.CLIENT_READ_WRITE
                : AuthIdentity.ANONYMOUS);
    }

    protected Properties maintainerProperties() {
        return sdkProperties(AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS);
    }

    protected Properties invalidCredentialProperties() {
        Properties result = sdkProperties(AuthIdentity.CLIENT_READ_WRITE);
        result.setProperty(PropertyKeyConst.PASSWORD, "invalid-" + UUID.randomUUID());
        return result;
    }

    protected Properties sdkProperties(AuthIdentity identity) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        if (AuthIdentity.ANONYMOUS != identity) {
            properties.setProperty(PropertyKeyConst.USERNAME,
                    requiredProperty(identity.usernameProperty));
            properties.setProperty(PropertyKeyConst.PASSWORD,
                    requiredProperty(identity.passwordProperty));
        }
        return properties;
    }

    /**
     * Grant the ordinary SDK test identity read visibility to an administrator-owned fixture.
     *
     * @param namespaceId namespace identifier
     * @param resourceType AI resource type
     * @param resourceName AI resource name
     * @throws Exception when the fixture grant cannot be created
     */
    protected void grantClientReadVisibility(String namespaceId, String resourceType,
            String resourceName) throws Exception {
        grantClientVisibility(namespaceId, resourceType, resourceName, "r");
    }

    /**
     * Grant the ordinary SDK test identity read/write visibility to an administrator-owned
     * fixture.
     *
     * @param namespaceId namespace identifier
     * @param resourceType AI resource type
     * @param resourceName AI resource name
     * @throws Exception when the fixture grant cannot be created
     */
    protected void grantClientReadWriteVisibility(String namespaceId, String resourceType,
            String resourceName) throws Exception {
        grantClientVisibility(namespaceId, resourceType, resourceName, "w");
    }

    /**
     * Attach the administrator identity required by an explicit Console API fixture request.
     *
     * @param connection HTTP connection to the standalone Console service
     * @throws Exception when administrator authentication fails
     */
    protected void authorizeAdmin(HttpURLConnection connection) throws Exception {
        if (AUTH_ENABLED) {
            connection.setRequestProperty("Authorization", "Bearer " + adminAccessToken());
        }
    }
    
    protected void addCleanup(CleanupAction cleanupAction) {
        cleanupActions.addFirst(cleanupAction);
    }
    
    protected String randomDataId(String scenario) {
        return "java-sdk-it-" + scenario + "-" + randomSuffix() + ".data";
    }
    
    protected String randomGroup(String scenario) {
        return "JAVA_SDK_IT_" + scenario.toUpperCase(Locale.ROOT) + "_"
                + randomSuffix().toUpperCase(Locale.ROOT);
    }
    
    protected String randomServiceName(String scenario) {
        return "java-sdk-it-" + scenario + "-" + randomSuffix();
    }
    
    protected int randomPort() {
        return TEST_ENDPOINT_PORT_MIN + Math.floorMod(NEXT_TEST_ENDPOINT_PORT.getAndIncrement(),
                TEST_ENDPOINT_PORT_RANGE);
    }
    
    protected void waitUntil(String reason, CheckedCondition condition) throws Exception {
        waitUntil(reason, 10000L, condition);
    }

    protected void waitUntil(String reason, long timeoutMillis, CheckedCondition condition)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.evaluate()) {
                    return;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
            Thread.sleep(500);
        }
        if (null == lastFailure) {
            fail(reason);
        }
        fail(reason + ", last failure: " + lastFailure.getMessage(), lastFailure);
    }
    
    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void grantClientVisibility(String namespaceId, String resourceType,
            String resourceName, String action) throws Exception {
        if (!AUTH_ENABLED) {
            return;
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("namespaceId", namespaceId);
        parameters.put("resourceType", resourceType);
        parameters.put("resourceName", resourceName);
        parameters.put("username", requiredProperty(AuthIdentity.CLIENT_READ_WRITE.usernameProperty));
        parameters.put("action", action);
        requestVisibilityGrant("POST", parameters);
        addCleanup(() -> requestVisibilityGrant("DELETE", parameters));
    }

    private void requestVisibilityGrant(String method, Map<String, String> parameters)
            throws Exception {
        String query = encodeForm(parameters);
        URL url = new URL(SERVER_HTTP_BASE_URL + AUTH_VISIBILITY_PATH + '?' + query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + adminAccessToken());
        try {
            JsonNode response = readJsonResponse(connection);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || response.path("code").asInt(-1) != 0) {
                throw new AssertionError("Visibility fixture request failed: HTTP "
                        + connection.getResponseCode() + ", response=" + response);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String adminAccessToken() throws Exception {
        if (adminAccessToken != null) {
            return adminAccessToken;
        }
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("username", requiredProperty(AuthIdentity.ADMIN.usernameProperty));
        credentials.put("password", requiredProperty(AuthIdentity.ADMIN.passwordProperty));
        byte[] body = encodeForm(credentials).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                SERVER_HTTP_BASE_URL + AUTH_LOGIN_PATH).openConnection();
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);
        try {
            connection.getOutputStream().write(body);
            JsonNode response = readJsonResponse(connection);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || !response.hasNonNull("accessToken")) {
                throw new AssertionError("Administrator authentication for fixture setup failed: HTTP "
                        + connection.getResponseCode());
            }
            adminAccessToken = response.get("accessToken").asText();
            return adminAccessToken;
        } finally {
            connection.disconnect();
        }
    }

    private JsonNode readJsonResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        try (InputStream input = responseCode >= HttpURLConnection.HTTP_BAD_REQUEST
                ? connection.getErrorStream() : connection.getInputStream()) {
            String response = input == null ? "" : new String(input.readAllBytes(),
                    StandardCharsets.UTF_8);
            JsonNode result = JacksonUtils.toObj(response);
            return result == null ? JacksonUtils.toObj("{}") : result;
        }
    }

    private String encodeForm(Map<String, String> parameters) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private String requiredProperty(String propertyName) {
        String result = System.getProperty(propertyName, "");
        if (result.isBlank()) {
            String environmentName = propertyName.toUpperCase(Locale.ROOT)
                    .replace('.', '_').replace('-', '_');
            result = System.getenv().getOrDefault(environmentName, "");
        }
        if (result.isBlank()) {
            throw new IllegalStateException("Required test property is blank: " + propertyName);
        }
        return result;
    }
    
    private Exception runActions(Deque<CleanupAction> actions, Exception failure) {
        Exception result = failure;
        while (!actions.isEmpty()) {
            try {
                actions.removeFirst().run();
            } catch (Exception exception) {
                if (null == result && !isCleanupIgnorable(exception)) {
                    result = exception;
                } else if (null != result) {
                    result.addSuppressed(exception);
                }
            }
        }
        return result;
    }
    
    private boolean isCleanupIgnorable(Exception exception) {
        if (!(exception instanceof NacosException)) {
            return false;
        }
        NacosException nacosException = (NacosException) exception;
        return NacosException.NOT_FOUND == nacosException.getErrCode()
                || NacosException.RESOURCE_NOT_FOUND == nacosException.getErrCode();
    }
    
    @FunctionalInterface
    protected interface CleanupAction {
        
        void run() throws Exception;
    }
    
    @FunctionalInterface
    protected interface CheckedCondition {
        
        boolean evaluate() throws Exception;
    }

    protected enum AuthIdentity {
        ANONYMOUS(null, null),
        ADMIN("nacos.test.auth.admin.username", "nacos.test.auth.admin.password"),
        CLIENT_READ_WRITE("nacos.test.auth.client.username",
                "nacos.test.auth.client.password"),
        CLIENT_READ_ONLY("nacos.test.auth.readonly.username",
                "nacos.test.auth.readonly.password"),
        CLIENT_NO_PERMISSION("nacos.test.auth.no-permission.username",
                "nacos.test.auth.no-permission.password");

        private final String usernameProperty;

        private final String passwordProperty;

        AuthIdentity(String usernameProperty, String passwordProperty) {
            this.usernameProperty = usernameProperty;
            this.passwordProperty = passwordProperty;
        }
    }
}
