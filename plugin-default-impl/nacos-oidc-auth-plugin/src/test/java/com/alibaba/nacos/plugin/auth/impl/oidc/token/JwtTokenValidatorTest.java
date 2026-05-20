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
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class JwtTokenValidatorTest {

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(JwtTokenValidator.class, "instance", null);
    }

    @Test
    void testExtractUsernameUsesConfiguredAndFallbackClaims() {
        OidcAuthConfig config = mockConfig();
        JwtTokenValidator validator = newValidator(config);

        assertEquals("configured", validator.extractUsername(new JWTClaimsSet.Builder()
            .claim("username", "configured").subject("subject").build()));
        assertEquals("preferred", validator.extractUsername(new JWTClaimsSet.Builder()
            .claim("preferred_username", "preferred").subject("subject").build()));
        assertEquals("email@nacos.io", validator.extractUsername(new JWTClaimsSet.Builder()
            .claim("email", "email@nacos.io").subject("subject").build()));
        assertEquals("subject", validator.extractUsername(new JWTClaimsSet.Builder()
            .subject("subject").build()));
    }

    @Test
    void testExtractRolesUsesConfiguredClaim() {
        OidcAuthConfig config = mockConfig();
        JwtTokenValidator validator = newValidator(config);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("roles", Arrays.asList("reader", "writer"))
            .build();

        assertEquals(Arrays.asList("reader", "writer"), validator.extractRoles(claims));
    }

    @Test
    void testExtractRolesUsesRealmAccessRoles() {
        JwtTokenValidator validator = newValidator(mockConfig());
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", Collections.singletonList("realm-admin"));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("realm_access", realmAccess)
            .build();

        assertEquals(Collections.singletonList("realm-admin"), validator.extractRoles(claims));
    }

    @Test
    void testExtractRolesUsesResourceAccessRoles() {
        OidcAuthConfig config = mockConfig();
        JwtTokenValidator validator = newValidator(config);
        Map<String, Object> clientAccess = new HashMap<>();
        clientAccess.put("roles", Collections.singletonList("client-admin"));
        Map<String, Object> resourceAccess = new HashMap<>();
        resourceAccess.put("nacos", clientAccess);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("resource_access", resourceAccess)
            .build();

        assertEquals(Collections.singletonList("client-admin"), validator.extractRoles(claims));
    }

    @Test
    void testExtractRolesUsesGroupsAndDefaultsToEmptyList() {
        JwtTokenValidator validator = newValidator(mockConfig());
        JWTClaimsSet groupClaims = new JWTClaimsSet.Builder()
            .claim("groups", Collections.singletonList("ops"))
            .build();
        JWTClaimsSet emptyClaims = new JWTClaimsSet.Builder().subject("subject").build();

        assertEquals(Collections.singletonList("ops"), validator.extractRoles(groupClaims));
        assertTrue(validator.extractRoles(emptyClaims).isEmpty());
    }

    @Test
    void testIsAdminChecksConfiguredAdminRole() {
        JwtTokenValidator validator = newValidator(mockConfig());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("roles", Arrays.asList("reader", "nacos-admin"))
            .build();

        assertTrue(validator.isAdmin(claims));
    }

    private JwtTokenValidator newValidator(OidcAuthConfig config) {
        ReflectionTestUtils.setField(JwtTokenValidator.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class);
                MockedStatic<JwksProvider> jwksStatic = mockStatic(JwksProvider.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            jwksStatic.when(JwksProvider::getInstance).thenReturn(mock(JwksProvider.class));
            return JwtTokenValidator.getInstance();
        }
    }

    private OidcAuthConfig mockConfig() {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        when(config.getUsernameClaim()).thenReturn("username");
        when(config.getRolesClaim()).thenReturn("roles");
        when(config.getClientId()).thenReturn("nacos");
        when(config.getAdminRole()).thenReturn("nacos-admin");
        return config;
    }
}
