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

package com.alibaba.nacos.test.adminapi.auth;

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

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
 *     <li>Expected capability: v1 and v3 login return the legacy flat token response for valid credentials.</li>
 *     <li>Boundary/validation: blank passwords are rejected with the same generic authentication failure.</li>
 *     <li>Exception/error handling: unknown users and known users with wrong passwords return identical HTTP status
 *     and response bodies without exposing whether the username exists.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class UserLoginAuthApiITCase extends OpenApiBaseITCase {

    private static final String AUTH_USER_PATH = nacosPath("/v3/auth/user");

    private static final String V3_LOGIN_PATH = nacosPath("/v3/auth/user/login");

    private static final String V1_LOGIN_PATH = nacosPath("/v1/auth/users/login");

    private static final String PASSWORD = "Nacos-login-it-123";

    private static final String LOGIN_FAILURE_MESSAGE =
            "User not found! Please check user exist or password is right!";

    @Test
    void testLoginResponseCompatibility() throws Exception {
        String username = "login_it_" + UUID.randomUUID();
        createUser(username);

        HttpResponse v3Success = waitForLoginSuccess(V3_LOGIN_PATH, username);
        assertEquals(200, v3Success.code(), v3Success.body());
        assertFlatTokenResponse(v3Success.body(), username);

        HttpResponse v1Success = postRaw(V1_LOGIN_PATH, credentials(username, PASSWORD));
        assertEquals(200, v1Success.code(), v1Success.body());
        assertFlatTokenResponse(v1Success.body(), username);

        HttpResponse v3Failure = verifyLoginFailureResponses(V3_LOGIN_PATH, username);
        HttpResponse v1Failure = verifyLoginFailureResponses(V1_LOGIN_PATH, username);
        assertEquals(v3Failure, v1Failure);
    }

    private HttpResponse verifyLoginFailureResponses(String loginPath, String username) throws Exception {
        HttpResponse wrongPassword = postRaw(loginPath, credentials(username, "wrong-password"));
        HttpResponse unknownUser = postRaw(loginPath,
            credentials("missing_" + UUID.randomUUID(), "wrong-password"));
        HttpResponse blankPassword = postRaw(loginPath, credentials(username, ""));

        assertLoginFailure(wrongPassword);
        assertEquals(wrongPassword, unknownUser);
        assertEquals(wrongPassword, blankPassword);
        return wrongPassword;
    }

    private HttpResponse waitForLoginSuccess(String loginPath, String username) throws Exception {
        HttpResponse response = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            response = postRaw(loginPath, credentials(username, PASSWORD));
            if (response.code() == 200) {
                return response;
            }
            Thread.sleep(1000L);
        }
        return response;
    }

    private void createUser(String username) throws Exception {
        postFormOk(AUTH_USER_PATH, credentials(username, PASSWORD));
        addCleanup(() -> deleteQuietly(AUTH_USER_PATH,
            Query.newInstance().addParam("username", username)));
    }

    private Query credentials(String username, String password) {
        return Query.newInstance().addParam("username", username).addParam("password", password);
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

    private void assertLoginFailure(HttpResponse response) {
        assertEquals(403, response.code(), response.body());
        assertEquals(LOGIN_FAILURE_MESSAGE, response.body());
    }
}
