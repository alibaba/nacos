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

package com.alibaba.nacos.test.adminapi.auth;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the default auth login APIs.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: v1 and v3 login preserve the legacy flat token response.</li>
 *     <li>Boundary/validation: blank passwords produce the generic authentication failure.</li>
 *     <li>Error handling: unknown users and wrong passwords return the same status and body.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class UserLoginAuthApiITCase {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(UserLoginAuthApiITCase.class);
    
    private static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    private static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    private static final String BASE_URL = "http://" + NACOS_HOST + ":" + NACOS_PORT;
    
    private static final String AUTH_USER_PATH = "/nacos/v3/auth/user";
    
    private static final String V3_LOGIN_PATH = AUTH_USER_PATH + "/login";
    
    private static final String V1_LOGIN_PATH = "/nacos/v1/auth/users/login";
    
    private static final String PASSWORD = "Nacos-login-it-123";
    
    private static final String LOGIN_FAILURE_MESSAGE =
        "User not found! Please check user exist or password is right!";
    
    private CloseableHttpClient httpClient;
    
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    public void setUp() {
        httpClient = HttpClientBuilder.create().build();
        nacosRestTemplate = new NacosRestTemplate(LOGGER,
            new DefaultHttpClientRequest(httpClient, RequestConfig.DEFAULT));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        nacosRestTemplate.close();
    }
    
    @Test
    public void testLoginResponseCompatibility() throws Exception {
        String username = "login_it_" + UUID.randomUUID();
        try {
            createUser(username);
            
            HttpRestResult<String> v3Success = waitForLoginSuccess(V3_LOGIN_PATH, username);
            assertEquals(200, v3Success.getCode(), responseBody(v3Success));
            assertFlatTokenResponse(v3Success.getData(), username);
            
            HttpRestResult<String> v1Success = postLogin(V1_LOGIN_PATH, username, PASSWORD);
            assertEquals(200, v1Success.getCode(), responseBody(v1Success));
            assertFlatTokenResponse(v1Success.getData(), username);
            
            HttpRestResult<String> v3Failure = verifyLoginFailures(V3_LOGIN_PATH, username);
            HttpRestResult<String> v1Failure = verifyLoginFailures(V1_LOGIN_PATH, username);
            assertEquals(v3Failure.getCode(), v1Failure.getCode());
            assertEquals(responseBody(v3Failure), responseBody(v1Failure));
        } finally {
            deleteUser(username);
        }
    }
    
    private void createUser(String username) throws Exception {
        HttpRestResult<String> response = nacosRestTemplate.postForm(BASE_URL + AUTH_USER_PATH,
            Header.EMPTY, credentials(username, PASSWORD), String.class);
        assertTrue(response.ok(), "create user failed: " + responseBody(response));
        JsonNode result = JacksonUtils.toObj(response.getData());
        assertEquals(0, result.get("code").asInt(), response.getData());
        assertEquals("create user ok!", result.get("data").asText(), response.getData());
    }
    
    private HttpRestResult<String> verifyLoginFailures(String path, String username)
        throws Exception {
        HttpRestResult<String> wrongPassword = postLogin(path, username, "wrong-password");
        HttpRestResult<String> unknownUser = postLogin(path,
            "missing_" + UUID.randomUUID(), "wrong-password");
        HttpRestResult<String> blankPassword = postLogin(path, username, "");
        
        assertLoginFailure(wrongPassword);
        assertEquals(wrongPassword.getCode(), unknownUser.getCode());
        assertEquals(responseBody(wrongPassword), responseBody(unknownUser));
        assertEquals(wrongPassword.getCode(), blankPassword.getCode());
        assertEquals(responseBody(wrongPassword), responseBody(blankPassword));
        return wrongPassword;
    }
    
    private HttpRestResult<String> waitForLoginSuccess(String path, String username)
        throws Exception {
        HttpRestResult<String> response = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            response = postLogin(path, username, PASSWORD);
            if (response.ok()) {
                return response;
            }
            Thread.sleep(1000L);
        }
        return response;
    }
    
    private HttpRestResult<String> postLogin(String path, String username, String password)
        throws Exception {
        return nacosRestTemplate.postForm(BASE_URL + path, Header.EMPTY,
            credentials(username, password), String.class);
    }
    
    private Map<String, String> credentials(String username, String password) {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        return credentials;
    }
    
    private void assertFlatTokenResponse(String body, String username) throws Exception {
        JsonNode root = JacksonUtils.toObj(body);
        assertNotNull(root, body);
        assertFalse(root.has("code"), body);
        assertTrue(root.hasNonNull("accessToken"), body);
        assertTrue(root.get("tokenTtl").asLong() > 0, body);
        assertFalse(root.get("globalAdmin").asBoolean(), body);
        assertEquals(username, root.get("username").asText(), body);
    }
    
    private void assertLoginFailure(HttpRestResult<String> response) {
        assertEquals(403, response.getCode(), responseBody(response));
        assertEquals(LOGIN_FAILURE_MESSAGE, responseBody(response));
    }
    
    private String responseBody(HttpRestResult<String> response) {
        return response.getData() == null ? response.getMessage() : response.getData();
    }
    
    private void deleteUser(String username) throws Exception {
        Query query = Query.newInstance().addParam("username", username);
        HttpRestResult<String> response = nacosRestTemplate.delete(BASE_URL + AUTH_USER_PATH,
            Header.EMPTY, query, String.class);
        if (!response.ok()) {
            LOGGER.warn("delete user non-OK: code={} body={}", response.getCode(),
                responseBody(response));
        }
    }
}
