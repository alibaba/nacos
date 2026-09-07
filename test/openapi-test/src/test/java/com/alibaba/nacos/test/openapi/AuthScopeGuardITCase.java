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

package com.alibaba.nacos.test.openapi;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fail-fast guard for the auth-enabled integration-test environment.
 *
 * <p>The guard proves that all three HTTP auth scopes reject anonymous requests and that the
 * intended test identities can enter normal business paths. It also keeps Nacos credentials
 * away from the independent ARD endpoint.</p>
 *
 * @author Nacos
 */
public class AuthScopeGuardITCase extends OpenApiBaseITCase {

    private static final String CLIENT_PROBE = nacosPath(
            "/v3/client/ns/instance/list?namespaceId=public&groupName=DEFAULT_GROUP"
                    + "&serviceName=nacos-it-auth-scope-guard");

    private static final String ADMIN_PROBE = nacosPath("/v3/admin/core/namespace/list");

    private static final String CONSOLE_PORT = System.getProperty("nacos.console.port", "8080");

    private static final String CONSOLE_PROBE = "http://" + NACOS_HOST + ':' + CONSOLE_PORT
            + "/v3/console/core/namespace?namespaceId=public";

    private static final String CONSOLE_LIVENESS_PROBE = "http://" + NACOS_HOST + ':'
            + CONSOLE_PORT + "/v3/console/health/liveness";

    private static final String ARD_PORT = System.getProperty("nacos.ai.registry.port", "9080");

    private static final String ARD_PROBE = "http://" + NACOS_HOST + ':' + ARD_PORT
            + "/.well-known/ai-catalog.json";

    @Test
    void shouldGuardClientAdminAndConsoleAuthScopes() throws Exception {
        assumeTrue(AUTH_ENABLED, "Auth Scope Guard only runs in an auth-enabled environment");

        assertDenied(getRaw(CLIENT_PROBE, AuthIdentity.ANONYMOUS));
        assertDenied(getRaw(CLIENT_PROBE, AuthIdentity.INVALID));
        assertDenied(getRaw(CLIENT_PROBE, AuthIdentity.CLIENT_NO_PERMISSION));
        assertSuccessResponse(getRaw(CLIENT_PROBE, AuthIdentity.CLIENT_READ_ONLY));
        assertSuccessResponse(getRaw(CLIENT_PROBE, AuthIdentity.CLIENT_READ_WRITE));

        assertDenied(getRaw(ADMIN_PROBE, AuthIdentity.ANONYMOUS));
        assertDenied(getRaw(ADMIN_PROBE, AuthIdentity.CLIENT_READ_WRITE));
        assertSuccessResponse(getRaw(ADMIN_PROBE, AuthIdentity.ADMIN));

        assertDenied(executeRaw(new HttpGet(CONSOLE_PROBE), AuthIdentity.ANONYMOUS));
        assertDenied(executeRaw(new HttpGet(CONSOLE_PROBE), AuthIdentity.CLIENT_READ_WRITE));
        assertSuccessResponse(executeRaw(new HttpGet(CONSOLE_PROBE), AuthIdentity.ADMIN));

        HttpResponse liveness = executeRaw(new HttpGet(CONSOLE_LIVENESS_PROBE),
                AuthIdentity.ANONYMOUS);
        assertSuccessResponse(liveness);
    }

    @Test
    void shouldKeepNacosCredentialsOutOfExternalRequests() throws Exception {
        assumeTrue(AUTH_ENABLED, "Auth Scope Guard only runs in an auth-enabled environment");

        HttpResponse response = executeExternalRaw(new HttpGet(ARD_PROBE));
        assertEquals(200, response.code(), response.body());

        HttpGet requestWithCredential = new HttpGet(ARD_PROBE);
        requestWithCredential.setHeader(HttpHeaders.AUTHORIZATION, "Bearer must-not-leak");
        assertThrows(IllegalArgumentException.class,
                () -> executeExternalRaw(requestWithCredential));
    }

    private void assertDenied(HttpResponse response) throws Exception {
        assertEquals(403, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertEquals(10001, root.path("code").asInt(), response.body());
    }

    private void assertSuccessResponse(HttpResponse response) throws Exception {
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertEquals(0, root.path("code").asInt(), response.body());
        assertTrue(root.has("data"), response.body());
    }
}
