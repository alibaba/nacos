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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcAuthPluginConfigTest {
    
    @Test
    void testDefaults() {
        OidcAuthPluginConfig config = OidcAuthPluginConfig.defaults();
        
        assertFalse(config.isValid());
        assertTrue(config.isJwtValidation());
        assertFalse(config.isIntrospectionValidation());
        assertEquals("", config.getIssuerUri());
        assertEquals("", config.getClientId());
        assertEquals("", config.getClientSecret());
        assertEquals(OidcAuthPluginConfig.DEFAULT_SCOPE, config.getScope());
        assertEquals(OidcAuthPluginConfig.DEFAULT_TOKEN_VALIDATION_METHOD,
            config.getTokenValidationMethod());
        assertEquals(OidcAuthPluginConfig.DEFAULT_JWKS_CACHE_TTL_SECONDS,
            config.getJwksCacheTtlSeconds());
        assertEquals(OidcAuthPluginConfig.DEFAULT_USERNAME_CLAIM, config.getUsernameClaim());
        assertEquals(OidcAuthPluginConfig.DEFAULT_ROLES_CLAIM, config.getRolesClaim());
        assertEquals(OidcAuthPluginConfig.DEFAULT_ADMIN_ROLE, config.getAdminRole());
        assertTrue(config.isAutoCreateUser());
        assertEquals("", config.getAuthorizationEndpoint());
        assertEquals(OidcAuthPluginConfig.DEFAULT_AUTHORIZATION_TIMEOUT_MS,
            config.getAuthorizationTimeoutMs());
        assertTrue(config.isStrictNonceValidation());
        assertTrue(config.isStrictAudienceValidation());
        assertEquals(14, config.toMap().size());
    }
    
    @Test
    void testFromParsesCompleteConfiguration() {
        Map<String, String> values = completeValues();
        
        OidcAuthPluginConfig config = OidcAuthPluginConfig.from(values);
        
        assertTrue(config.isValid());
        assertFalse(config.isJwtValidation());
        assertTrue(config.isIntrospectionValidation());
        assertEquals("http://issuer", config.getIssuerUri());
        assertEquals("client", config.getClientId());
        assertEquals("secret", config.getClientSecret());
        assertEquals("openid", config.getScope());
        assertEquals(12L, config.getJwksCacheTtlSeconds());
        assertEquals("email", config.getUsernameClaim());
        assertEquals("groups", config.getRolesClaim());
        assertEquals("admin", config.getAdminRole());
        assertFalse(config.isAutoCreateUser());
        assertEquals("http://idp/authz", config.getAuthorizationEndpoint());
        assertEquals(99L, config.getAuthorizationTimeoutMs());
        assertFalse(config.isStrictNonceValidation());
        assertFalse(config.isStrictAudienceValidation());
        assertEquals(values, config.toMap());
    }
    
    @Test
    void testBlankValuesUseDefaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(OidcAuthPluginConfig.SCOPE, "  ");
        values.put(OidcAuthPluginConfig.CLIENT_ID, "");
        
        OidcAuthPluginConfig config = OidcAuthPluginConfig.from(values);
        
        assertEquals(OidcAuthPluginConfig.DEFAULT_SCOPE, config.getScope());
        assertEquals("", config.getClientId());
    }
    
    @Test
    void testRejectsNullInvalidNumbersAndInvalidBooleans() {
        assertInvalid(OidcAuthPluginConfig.CLIENT_ID, null);
        assertInvalid(OidcAuthPluginConfig.JWKS_CACHE_TTL_SECONDS, "not-number");
        assertInvalid(OidcAuthPluginConfig.JWKS_CACHE_TTL_SECONDS, "0");
        assertInvalid(OidcAuthPluginConfig.AUTHORIZATION_TIMEOUT_MS, "-1");
        assertInvalid(OidcAuthPluginConfig.AUTO_CREATE_USER, "yes");
        assertInvalid(OidcAuthPluginConfig.STRICT_NONCE_VALIDATION, "enabled");
        assertInvalid(OidcAuthPluginConfig.STRICT_AUDIENCE_VALIDATION, "disabled");
    }
    
    private void assertInvalid(String key, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(key, value);
        assertThrows(IllegalArgumentException.class,
            () -> OidcAuthPluginConfig.from(values));
    }
    
    private Map<String, String> completeValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(OidcAuthPluginConfig.ISSUER_URI, "http://issuer");
        values.put(OidcAuthPluginConfig.CLIENT_ID, "client");
        values.put(OidcAuthPluginConfig.CLIENT_SECRET, "secret");
        values.put(OidcAuthPluginConfig.SCOPE, "openid");
        values.put(OidcAuthPluginConfig.TOKEN_VALIDATION_METHOD, "introspection");
        values.put(OidcAuthPluginConfig.JWKS_CACHE_TTL_SECONDS, "12");
        values.put(OidcAuthPluginConfig.USERNAME_CLAIM, "email");
        values.put(OidcAuthPluginConfig.ROLES_CLAIM, "groups");
        values.put(OidcAuthPluginConfig.ADMIN_ROLE, "admin");
        values.put(OidcAuthPluginConfig.AUTO_CREATE_USER, "false");
        values.put(OidcAuthPluginConfig.AUTHORIZATION_ENDPOINT, "http://idp/authz");
        values.put(OidcAuthPluginConfig.AUTHORIZATION_TIMEOUT_MS, "99");
        values.put(OidcAuthPluginConfig.STRICT_NONCE_VALIDATION, "false");
        values.put(OidcAuthPluginConfig.STRICT_AUDIENCE_VALIDATION, "false");
        return values;
    }
}
