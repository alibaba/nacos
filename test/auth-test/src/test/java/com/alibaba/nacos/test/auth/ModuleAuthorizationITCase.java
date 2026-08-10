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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Representative authorization scenarios for Nacos HTTP API modules.
 *
 * <p>Each scenario verifies missing identity, invalid identity, valid identity without
 * authority, and valid identity with the required authority.</p>
 *
 * @author Nacos
 */
public class ModuleAuthorizationITCase extends AuthITCase {

    @Test
    void testConfigOpenApiAuthorization() throws Exception {
        verifyAuthorization("config", SERVER_BASE_URL,
                CONTEXT_PATH + "/v3/client/cs/config?namespaceId=public"
                        + "&groupName=DEFAULT_GROUP&dataId=auth-it-missing",
                "public:DEFAULT_GROUP:config/auth-it-missing");
    }

    @Test
    void testNamingAdminApiAuthorization() throws Exception {
        verifyAuthorization("naming", SERVER_BASE_URL,
                CONTEXT_PATH + "/v3/admin/ns/service/list?namespaceId=public"
                        + "&pageNo=1&pageSize=10",
                "public:*:naming/*");
    }

    @Test
    void testAiAdminApiAuthorization() throws Exception {
        verifyAuthorization("ai", SERVER_BASE_URL,
                CONTEXT_PATH + "/v3/admin/ai/agents/list?namespaceId=public"
                        + "&pageNo=1&pageSize=10",
                "public:DEFAULT_GROUP:ai/*");
    }

    @Test
    void testCoreAdminApiAuthorization() throws Exception {
        verifyAuthorization("core", SERVER_BASE_URL,
                CONTEXT_PATH + "/v3/admin/core/namespace/list",
                "/v3/admin/core/namespace");
    }

    @Test
    void testConsoleApiAuthorization() throws Exception {
        verifyAuthorization("console", CONSOLE_BASE_URL,
                "/v3/console/cs/config/list?namespaceId=public&pageNo=1&pageSize=10",
                "public:*:config/*");
    }

    private void verifyAuthorization(String prefix, String baseUrl, String path,
            String permissionResource) throws Exception {
        assertDenied(get(baseUrl, path, null));
        assertDenied(get(baseUrl, path, "invalid-token"));

        TestIdentity identity = createIdentityWithoutPermission(prefix);
        assertDenied(get(baseUrl, path, identity.token()));

        grantReadPermission(identity, permissionResource);
        assertEquals(200, get(baseUrl, path, identity.token()).status());
    }
}
