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

package com.alibaba.nacos.plugin.auth.impl.oidc.token;

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class JwksProviderTest {

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(JwksProvider.class, "instance", null);
    }

    @Test
    void testGetJwkSetRejectsMissingIssuerAndJwksUri() {
        OidcAuthConfig config = mockConfig("", "");
        JwksProvider provider = newProvider(config);

        assertThrows(IOException.class, provider::getJwkSet);
    }

    @Test
    void testClearCacheClearsCachedJwksUri() {
        OidcAuthConfig config = mockConfig("http://issuer/jwks", "");
        JwksProvider provider = newProvider(config);
        ReflectionTestUtils.setField(provider, "jwksUri", "http://issuer/jwks");

        provider.clearCache();

        assertNull(ReflectionTestUtils.getField(provider, "jwksUri"));
    }

    private JwksProvider newProvider(OidcAuthConfig config) {
        ReflectionTestUtils.setField(JwksProvider.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            return JwksProvider.getInstance();
        }
    }

    private OidcAuthConfig mockConfig(String jwksUri, String issuerUri) {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        when(config.getJwksCacheTtlSeconds()).thenReturn(60L);
        when(config.getJwksUri()).thenReturn(jwksUri);
        when(config.getIssuerUri()).thenReturn(issuerUri);
        return config;
    }
}
