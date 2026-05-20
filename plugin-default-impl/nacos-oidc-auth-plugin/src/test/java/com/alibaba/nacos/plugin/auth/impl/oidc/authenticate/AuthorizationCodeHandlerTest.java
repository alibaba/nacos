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

package com.alibaba.nacos.plugin.auth.impl.oidc.authenticate;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuthorizationCodeHandlerTest {

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(AuthorizationCodeHandler.class, "instance", null);
    }

    @Test
    void testBuildAuthorizationUrlRejectsMissingEndpoint() {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        AuthorizationCodeHandler handler = newHandler(config);

        assertThrows(AccessException.class,
            () -> handler.buildAuthorizationUrl("http://nacos/callback"));
    }

    @Test
    void testBuildAuthorizationUrlIncludesOidcParameters() throws AccessException {
        OidcAuthConfig config = mockConfig();
        AuthorizationCodeHandler handler = newHandler(config);

        String url = handler.buildAuthorizationUrl("http://nacos/callback");

        assertTrue(url.startsWith("http://idp/authorize?"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id=client"));
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Fnacos%2Fcallback"));
        assertTrue(url.contains("scope=openid+profile"));
        assertTrue(url.contains("state="));
        assertTrue(url.contains("nonce="));
    }

    @Test
    void testBuildLogoutUrlReturnsNullWhenEndpointMissing() {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        AuthorizationCodeHandler handler = newHandler(config);

        assertNull(handler.buildLogoutUrl("id-token", "http://nacos"));
    }

    @Test
    void testBuildLogoutUrlWithOptionalParameters() {
        OidcAuthConfig config = mockConfig();
        AuthorizationCodeHandler handler = newHandler(config);

        String logoutUrl = handler.buildLogoutUrl("id-token", "http://nacos");

        assertTrue(logoutUrl.startsWith("http://idp/logout?"));
        assertTrue(logoutUrl.contains("id_token_hint=id-token"));
        assertTrue(logoutUrl.contains("post_logout_redirect_uri=http://nacos"));
        assertTrue(logoutUrl.contains("client_id=client"));
    }

    @Test
    void testBuildLogoutUrlWithClientOnly() {
        OidcAuthConfig config = mockConfig();
        AuthorizationCodeHandler handler = newHandler(config);

        String logoutUrl = handler.buildLogoutUrl("", "");

        assertTrue(logoutUrl.startsWith("http://idp/logout?"));
        assertTrue(logoutUrl.endsWith("&client_id=client"));
    }

    private AuthorizationCodeHandler newHandler(OidcAuthConfig config) {
        ReflectionTestUtils.setField(AuthorizationCodeHandler.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class);
                MockedStatic<JwtTokenValidator> validatorStatic =
                    mockStatic(JwtTokenValidator.class);
                MockedStatic<OidcUserMapper> mapperStatic = mockStatic(OidcUserMapper.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            validatorStatic.when(JwtTokenValidator::getInstance)
                .thenReturn(mock(JwtTokenValidator.class));
            mapperStatic.when(OidcUserMapper::getInstance).thenReturn(mock(OidcUserMapper.class));
            return AuthorizationCodeHandler.getInstance();
        }
    }

    private OidcAuthConfig mockConfig() {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        when(config.getAuthorizationEndpoint()).thenReturn("http://idp/authorize");
        when(config.getClientId()).thenReturn("client");
        when(config.getClientSecret()).thenReturn("secret");
        when(config.getScope()).thenReturn("openid profile");
        when(config.getEndSessionEndpoint()).thenReturn("http://idp/logout");
        return config;
    }
}
