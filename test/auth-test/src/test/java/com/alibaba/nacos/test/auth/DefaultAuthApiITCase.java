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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth-enabled integration tests for the default user, role, and permission APIs.
 *
 * @author Nacos
 */
public class DefaultAuthApiITCase extends AuthITCase {

    private static final String USER_PATH = CONTEXT_PATH + "/v3/auth/user";

    private static final String ROLE_PATH = CONTEXT_PATH + "/v3/auth/role";

    private static final String PERMISSION_PATH = CONTEXT_PATH + "/v3/auth/permission";

    @Test
    void testUserRoleAndPermissionManagement() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "auth-api-" + suffix;
        String role = "ROLE_AUTH_API_" + suffix;
        String resource = "public:DEFAULT_GROUP:config/auth-api-" + suffix;

        assertSuccess(postForm(SERVER_BASE_URL, USER_PATH, adminToken(),
                params("username", username, "password", "AuthTest123!")));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, USER_PATH, adminToken(),
                params("username", username)));

        String userToken = awaitLogin(username, "AuthTest123!");
        assertDenied(postForm(SERVER_BASE_URL, USER_PATH, userToken,
                params("username", "forbidden-" + suffix, "password", "AuthTest123!")));

        JsonNode users = assertSuccess(get(SERVER_BASE_URL,
                USER_PATH + "/list?pageNo=1&pageSize=20&username=" + username,
                adminToken()));
        assertTrue(users.toString().contains(username), users.toString());

        assertSuccess(postForm(SERVER_BASE_URL, ROLE_PATH, adminToken(),
                params("role", role, "username", username)));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, ROLE_PATH, adminToken(),
                params("role", role, "username", username)));

        JsonNode roles = assertSuccess(get(SERVER_BASE_URL,
                ROLE_PATH + "/list?pageNo=1&pageSize=20&username=" + username,
                adminToken()));
        assertTrue(roles.toString().contains(role), roles.toString());

        assertSuccess(postForm(SERVER_BASE_URL, PERMISSION_PATH, adminToken(),
                params("role", role, "resource", resource, "action", "r")));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, PERMISSION_PATH, adminToken(),
                params("role", role, "resource", resource, "action", "r")));

        JsonNode duplicate = assertSuccess(get(SERVER_BASE_URL,
                PERMISSION_PATH + "?role=" + role + "&resource=" + resource + "&action=r",
                adminToken()));
        assertTrue(duplicate.get("data").asBoolean(), duplicate.toString());

        assertSuccess(deleteForm(SERVER_BASE_URL, PERMISSION_PATH, adminToken(),
                params("role", role, "resource", resource, "action", "r")));
        JsonNode removed = assertSuccess(get(SERVER_BASE_URL,
                PERMISSION_PATH + "?role=" + role + "&resource=" + resource + "&action=r",
                adminToken()));
        assertFalse(removed.get("data").asBoolean(), removed.toString());
    }
}
