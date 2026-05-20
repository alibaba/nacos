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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcAuthConfigTest {

    private ConfigurableEnvironment originalEnvironment;

    @BeforeEach
    void setUp() {
        originalEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        ReflectionTestUtils.setField(OidcAuthConfig.class, "instance", null);
    }

    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(originalEnvironment);
        ReflectionTestUtils.setField(OidcAuthConfig.class, "instance", null);
    }

    @Test
    void testLoadDefaultConfiguration() {
        OidcAuthConfig config = OidcAuthConfig.getInstance();

        assertFalse(config.isValid());
        assertTrue(config.isJwtValidation());
        assertFalse(config.isIntrospectionValidation());
        assertEquals(OidcConstants.DEFAULT_SCOPE, config.getScope());
        assertEquals(OidcConstants.DEFAULT_JWKS_CACHE_TTL_SECONDS,
            config.getJwksCacheTtlSeconds());
        assertEquals(OidcConstants.DEFAULT_USERNAME_CLAIM, config.getUsernameClaim());
        assertEquals(OidcConstants.DEFAULT_ROLES_CLAIM, config.getRolesClaim());
        assertEquals(OidcConstants.DEFAULT_ADMIN_ROLE, config.getAdminRole());
        assertTrue(config.isAutoCreateUser());
        assertTrue(config.isStrictNonceValidation());
        assertTrue(config.isStrictAudienceValidation());
        assertEquals(OidcConstants.DEFAULT_AUTHORIZATION_TIMEOUT_MS,
            config.getAuthorizationTimeoutMs());
    }

    @Test
    void testReloadReadsEnvironmentProperties() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(OidcConstants.CONFIG_CLIENT_ID, "client")
            .withProperty(OidcConstants.CONFIG_CLIENT_SECRET, "secret")
            .withProperty(OidcConstants.CONFIG_SCOPE, "openid")
            .withProperty(OidcConstants.CONFIG_TOKEN_VALIDATION_METHOD, "introspection")
            .withProperty(OidcConstants.CONFIG_JWKS_CACHE_TTL, "12")
            .withProperty(OidcConstants.CONFIG_USERNAME_CLAIM, "email")
            .withProperty(OidcConstants.CONFIG_ROLES_CLAIM, "groups")
            .withProperty(OidcConstants.CONFIG_ADMIN_ROLE, "admin")
            .withProperty(OidcConstants.CONFIG_AUTO_CREATE_USER, "false")
            .withProperty(OidcConstants.CONFIG_STRICT_NONCE_VALIDATION, "false")
            .withProperty(OidcConstants.CONFIG_STRICT_AUDIENCE_VALIDATION, "false")
            .withProperty(OidcConstants.CONFIG_AUTHORIZATION_ENDPOINT, "http://idp/authz")
            .withProperty(OidcConstants.CONFIG_AUTHORIZATION_TIMEOUT_MS, "99");
        EnvUtil.setEnvironment(environment);

        OidcAuthConfig config = OidcAuthConfig.getInstance();

        assertEquals("client", config.getClientId());
        assertEquals("secret", config.getClientSecret());
        assertEquals("openid", config.getScope());
        assertTrue(config.isIntrospectionValidation());
        assertEquals(12L, config.getJwksCacheTtlSeconds());
        assertEquals("email", config.getUsernameClaim());
        assertEquals("groups", config.getRolesClaim());
        assertEquals("admin", config.getAdminRole());
        assertFalse(config.isAutoCreateUser());
        assertFalse(config.isStrictNonceValidation());
        assertFalse(config.isStrictAudienceValidation());
        assertEquals("http://idp/authz", config.getAuthorizationEvaluateEndpoint());
        assertEquals(99L, config.getAuthorizationTimeoutMs());
    }

    @Test
    void testSettersAndAutoConfigurationBean() {
        OidcAuthConfig config = OidcAuthConfig.getInstance();
        config.setIssuerUri("http://issuer");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setTokenValidationMethod("jwt");
        config.setJwksUri("http://issuer/jwks");
        config.setAuthorizationEndpoint("http://issuer/auth");
        config.setTokenEndpoint("http://issuer/token");
        config.setUserinfoEndpoint("http://issuer/userinfo");
        config.setEndSessionEndpoint("http://issuer/logout");
        config.setAuthorizationTimeoutMs(123L);
        config.setStrictNonceValidation(false);
        config.setStrictAudienceValidation(false);

        assertTrue(config.isValid());
        assertTrue(config.isJwtValidation());
        assertEquals("http://issuer", config.getIssuerUri());
        assertEquals("http://issuer/jwks", config.getJwksUri());
        assertEquals("http://issuer/auth", config.getAuthorizationEndpoint());
        assertEquals("http://issuer/token", config.getTokenEndpoint());
        assertEquals("http://issuer/userinfo", config.getUserinfoEndpoint());
        assertEquals("http://issuer/logout", config.getEndSessionEndpoint());
        assertEquals(123L, config.getAuthorizationTimeoutMs());
        assertFalse(config.isStrictNonceValidation());
        assertFalse(config.isStrictAudienceValidation());
        assertNotNull(new OidcPluginAutoConfiguration().oidcLoginController());
    }
}
