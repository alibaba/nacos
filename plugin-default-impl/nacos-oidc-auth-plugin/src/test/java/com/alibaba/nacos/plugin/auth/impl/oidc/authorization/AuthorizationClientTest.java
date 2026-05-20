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

package com.alibaba.nacos.plugin.auth.impl.oidc.authorization;

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuthorizationClientTest {

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(AuthorizationClient.class, "instance", null);
    }

    @Test
    void testAuthorizeAllowsWhenEndpointIsMissing() {
        OidcAuthConfig config = mockConfig("");
        AuthorizationClient client = newClient(config);

        AuthorizationResponse response = client.authorize(request());

        assertTrue(response.isAllowed());
        assertTrue(client.isAuthorized("token", "nacos:config", "read"));
    }

    @Test
    void testAuthorizeDeniesWhenEndpointIsInvalid() {
        OidcAuthConfig config = mockConfig("://bad-endpoint");
        AuthorizationClient client = newClient(config);

        AuthorizationResponse response = client.authorize(request());

        assertFalse(response.isAllowed());
        assertTrue(response.getReason().contains("Authorization error"));
    }

    private AuthorizationClient newClient(OidcAuthConfig config) {
        ReflectionTestUtils.setField(AuthorizationClient.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            return AuthorizationClient.getInstance();
        }
    }

    private OidcAuthConfig mockConfig(String endpoint) {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        when(config.getAuthorizationTimeoutMs()).thenReturn(1000L);
        when(config.getAuthorizationEvaluateEndpoint()).thenReturn(endpoint);
        return config;
    }

    private AuthorizationRequest request() {
        return AuthorizationRequest.builder()
            .token("token")
            .resource("nacos:config")
            .action("read")
            .build();
    }
}
