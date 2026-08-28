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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth-enabled integration tests for every default auth plugin v3 API.
 *
 * <p>Every protected endpoint is exercised without identity, with invalid identity, with a valid
 * non-administrator identity, and with the global administrator identity. Login and one-time
 * administrator bootstrap are intentionally public and are verified separately.</p>
 *
 * @author Nacos
 */
public class DefaultAuthApiITCase extends AuthITCase {

    private static final String USER_PATH = CONTEXT_PATH + "/v3/auth/user";

    private static final String ROLE_PATH = CONTEXT_PATH + "/v3/auth/role";

    private static final String PERMISSION_PATH = CONTEXT_PATH + "/v3/auth/permission";

    private static final String VISIBILITY_PATH = CONTEXT_PATH + "/v3/auth/visibility";

    private static final String SKILL_PATH = CONTEXT_PATH + "/v3/admin/ai/skills";

    @Test
    void testEveryProtectedUserApiAuthorizationAndCorrectness() throws Exception {
        TestIdentity unprivileged = createIdentityWithoutPermission("auth-user-api");
        String suffix = randomSuffix();
        String username = "managed-user-" + suffix;
        String password = "AuthTest123!";
        String newPassword = "AuthTest456!";

        verifyAdminOnly(unprivileged, token -> postForm(SERVER_BASE_URL, USER_PATH, token,
                params("username", username, "password", password)), response -> {
                    assertEquals("create user ok!", assertSuccess(response).get("data").asText());
                });
        addCleanup(() -> deleteForm(SERVER_BASE_URL, USER_PATH, adminToken(),
                params("username", username)));

        verifyAdminOnly(unprivileged, token -> get(SERVER_BASE_URL,
                USER_PATH + "/list?pageNo=1&pageSize=20&username=" + username, token),
                response -> assertTrue(assertSuccess(response).toString().contains(username)));

        verifyAdminOnly(unprivileged, token -> get(SERVER_BASE_URL,
                USER_PATH + "/search?username=" + username, token),
                response -> assertTrue(assertSuccess(response).toString().contains(username)));

        verifyAdminOnly(unprivileged, token -> putForm(SERVER_BASE_URL, USER_PATH, token,
                params("username", username, "newPassword", newPassword)), response -> {
                    assertEquals("update user ok!", assertSuccess(response).get("data").asText());
                });
        assertFalse(awaitLogin(username, newPassword).isBlank());

        verifyAdminOnly(unprivileged, token -> deleteForm(SERVER_BASE_URL, USER_PATH, token,
                params("username", username)), response -> {
                    assertEquals("delete user ok!", assertSuccess(response).get("data").asText());
                });
    }

    @Test
    void testPublicUserApisCorrectness() throws Exception {
        TestIdentity identity = createIdentityWithoutPermission("auth-login-api");
        assertFalse(identity.token().isBlank());

        Response wrongPassword = postForm(SERVER_BASE_URL, USER_PATH + "/login", null,
                params("username", identity.username(), "password", "wrong-password"));
        assertNotEquals(200, wrongPassword.status(), wrongPassword.body());
        assertFalse(wrongPassword.body().contains("accessToken"), wrongPassword.body());

        Response bootstrap = postForm(SERVER_BASE_URL, USER_PATH + "/admin", null,
                params("password", "AnotherAdmin123!"));
        assertEquals(200, bootstrap.status(), bootstrap.body());
        JsonNode bootstrapResult = JacksonUtils.toObj(bootstrap.body());
        assertEquals(409, bootstrapResult.get("code").asInt(), bootstrap.body());
    }

    @Test
    void testEveryRoleApiAuthorizationAndCorrectness() throws Exception {
        TestIdentity unprivileged = createIdentityWithoutPermission("auth-role-api");
        String role = "ROLE_MANAGED_" + randomSuffix().toUpperCase();

        verifyAdminOnly(unprivileged, token -> postForm(SERVER_BASE_URL, ROLE_PATH, token,
                params("role", role, "username", unprivileged.username())), response -> {
                    assertEquals("add role ok!", assertSuccess(response).get("data").asText());
                });
        addCleanup(() -> deleteForm(SERVER_BASE_URL, ROLE_PATH, adminToken(),
                params("role", role, "username", unprivileged.username())));

        verifyAdminOnly(unprivileged, token -> get(SERVER_BASE_URL,
                ROLE_PATH + "/list?pageNo=1&pageSize=20&username="
                        + unprivileged.username() + "&role=" + role, token),
                response -> assertTrue(assertSuccess(response).toString().contains(role)));

        verifyAdminOnly(unprivileged, token -> get(SERVER_BASE_URL,
                ROLE_PATH + "/search?role=" + role, token),
                response -> assertTrue(assertSuccess(response).toString().contains(role)));

        verifyAdminOnly(unprivileged, token -> deleteForm(SERVER_BASE_URL, ROLE_PATH, token,
                params("role", role, "username", unprivileged.username())), response -> {
                    assertTrue(assertSuccess(response).get("data").asText()
                            .contains("delete role of user"));
                });
    }

    @Test
    void testEveryPermissionApiAuthorizationAndCorrectness() throws Exception {
        TestIdentity unprivileged = createIdentityWithoutPermission("auth-permission-api");
        String resource = "public:DEFAULT_GROUP:config/auth-permission-" + randomSuffix();
        Map<String, String> permission = params("role", unprivileged.role(), "resource", resource,
                "action", "r");

        verifyAdminOnly(unprivileged,
                token -> postForm(SERVER_BASE_URL, PERMISSION_PATH, token, permission),
                response -> assertEquals("add permission ok!",
                        assertSuccess(response).get("data").asText()));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, PERMISSION_PATH, adminToken(), permission));

        verifyAdminOnly(unprivileged, token -> get(SERVER_BASE_URL,
                PERMISSION_PATH + "/list?pageNo=1&pageSize=20&role=" + unprivileged.role(),
                token), response -> assertTrue(assertSuccess(response).toString()
                        .contains(resource)));

        verifyAdminOnly(unprivileged, token -> get(SERVER_BASE_URL,
                PERMISSION_PATH + "?role=" + unprivileged.role() + "&resource=" + resource
                        + "&action=r", token), response -> assertTrue(
                        assertSuccess(response).get("data").asBoolean(), response.body()));

        verifyAdminOnly(unprivileged,
                token -> deleteForm(SERVER_BASE_URL, PERMISSION_PATH, token, permission),
                response -> assertEquals("delete permission ok!",
                        assertSuccess(response).get("data").asText()));
    }

    @Test
    void testEveryVisibilityApiAuthorizationAndCorrectness() throws Exception {
        TestIdentity unprivileged = createIdentityWithoutPermission("auth-visibility-api");
        String skillName = "visibility-skill-" + randomSuffix();
        Map<String, String> skill = skillDraft(skillName);
        assertSuccess(postForm(SERVER_BASE_URL, SKILL_PATH + "/draft", adminToken(), skill));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, SKILL_PATH, adminToken(),
                params("namespaceId", "public", "skillName", skillName)));

        Map<String, String> visibility = params("namespaceId", "public", "resourceType", "skill",
                "resourceName", skillName, "username", unprivileged.username(), "action", "r");

        verifyAdminOnly(unprivileged,
                token -> postForm(SERVER_BASE_URL, VISIBILITY_PATH, token, visibility),
                response -> assertEquals("grant visibility permission ok!",
                        assertSuccess(response).get("data").asText()));

        verifyAdminOnly(unprivileged,
                token -> deleteForm(SERVER_BASE_URL, VISIBILITY_PATH, token, visibility),
                response -> assertEquals("revoke visibility permission ok!",
                        assertSuccess(response).get("data").asText()));
    }

    private void verifyAdminOnly(TestIdentity unprivileged, AuthRequest request,
            AuthorizedAssertion authorizedAssertion) throws Exception {
        assertForbidden(request.execute(null));
        assertForbidden(request.execute("invalid-token"));
        assertForbidden(request.execute(unprivileged.token()));
        authorizedAssertion.verify(request.execute(adminToken()));
    }

    private void assertForbidden(Response response) {
        assertEquals(403, response.status(), response.body());
    }

    private Map<String, String> skillDraft(String skillName) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", skillName);
        card.put("description", "auth integration test skill");
        card.put("skillMd", "# " + skillName + "\n\nAuth integration test body.");
        card.put("resource", Map.of());
        return params("namespaceId", "public", "skillName", skillName,
                "targetVersion", "1.0.0", "skillCard", JacksonUtils.toJson(card),
                "commitMsg", "auth integration test");
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @FunctionalInterface
    private interface AuthRequest {

        Response execute(String token) throws Exception;
    }

    @FunctionalInterface
    private interface AuthorizedAssertion {

        void verify(Response response) throws Exception;
    }
}
