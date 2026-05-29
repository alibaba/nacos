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

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void testValidateRejectsBlankToken() {
        JwtTokenValidator validator = newValidator(mockConfig());
        
        assertThrows(AccessException.class, () -> validator.validate(" "));
    }
    
    @Test
    void testValidateReturnsClaimsWhenClaimsAreValid() throws Exception {
        OidcAuthConfig config = mockConfig();
        when(config.getIssuerUri()).thenReturn("http://issuer");
        JwtTokenValidator validator = newValidator(config);
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        JWTClaimsSet claims = validClaims().build();
        when(processor.process("token", null)).thenReturn(claims);
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        
        assertEquals(claims, validator.validate("token"));
    }
    
    @Test
    void testValidateRejectsExpiredOrFutureTokens() throws Exception {
        JwtTokenValidator validator = newValidator(mockConfig());
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        when(processor.process("expired", null)).thenReturn(validClaims()
            .expirationTime(new Date(System.currentTimeMillis() - 1_000L)).build());
        when(processor.process("future", null)).thenReturn(validClaims()
            .notBeforeTime(new Date(System.currentTimeMillis() + 60_000L)).build());
        
        assertThrows(AccessException.class, () -> validator.validate("expired"));
        assertThrows(AccessException.class, () -> validator.validate("future"));
    }
    
    @Test
    void testValidateAudienceAndIssuerBranches() throws Exception {
        OidcAuthConfig config = mockConfig();
        when(config.getIssuerUri()).thenReturn("http://issuer/");
        when(config.isStrictAudienceValidation()).thenReturn(false);
        JwtTokenValidator validator = newValidator(config);
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        when(processor.process("slash", null)).thenReturn(validClaims()
            .issuer("http://issuer").audience("other").build());
        when(processor.process("azp", null)).thenReturn(validClaims()
            .audience("other").claim("azp", "nacos").build());
        when(processor.process("issuer", null)).thenReturn(validClaims()
            .issuer("http://other").build());
        
        assertEquals("subject", validator.validate("slash").getSubject());
        assertEquals("nacos", validator.validate("azp").getStringClaim("azp"));
        assertThrows(AccessException.class, () -> validator.validate("issuer"));
        
        OidcAuthConfig noSlashConfig = mockConfig();
        when(noSlashConfig.getIssuerUri()).thenReturn("http://issuer");
        JwtTokenValidator noSlashValidator = newValidator(noSlashConfig);
        ConfigurableJWTProcessor<SecurityContext> noSlashProcessor = mockProcessor();
        ReflectionTestUtils.setField(noSlashValidator, "jwtProcessor", noSlashProcessor);
        when(noSlashProcessor.process("issuer-slash", null)).thenReturn(validClaims()
            .issuer("http://issuer/").build());
        
        assertEquals("subject", noSlashValidator.validate("issuer-slash").getSubject());
    }
    
    @Test
    void testValidateRejectsStrictAudienceMismatch() throws Exception {
        OidcAuthConfig config = mockConfig();
        when(config.isStrictAudienceValidation()).thenReturn(true);
        JwtTokenValidator validator = newValidator(config);
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        when(processor.process("token", null)).thenReturn(validClaims().audience("other").build());
        
        assertThrows(AccessException.class, () -> validator.validate("token"));
    }
    
    @Test
    void testValidateWrapsProcessorFailures() throws Exception {
        JwtTokenValidator validator = newValidator(mockConfig());
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        when(processor.process("parse", null)).thenThrow(new ParseException("bad", 0));
        when(processor.process("jose", null)).thenThrow(new JOSEException("broken"));
        when(processor.process("argument", null))
            .thenThrow(new IllegalArgumentException("broken"));
        when(processor.process("runtime", null)).thenThrow(new IllegalStateException("broken"));
        
        assertThrows(AccessException.class, () -> validator.validate("parse"));
        assertThrows(AccessException.class, () -> validator.validate("jose"));
        assertThrows(AccessException.class, () -> validator.validate("argument"));
        assertThrows(AccessException.class, () -> validator.validate("runtime"));
    }
    
    @Test
    void testValidateWrapsJwksInitializationAndRefreshFailures() throws Exception {
        JwtTokenValidator validator = newValidator(mockConfig());
        JwksProvider provider = mock(JwksProvider.class);
        when(provider.getJwkSet()).thenThrow(new IOException("down"));
        ReflectionTestUtils.setField(validator, "jwksProvider", provider);
        
        assertThrows(AccessException.class, () -> validator.validate("token"));
        
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        when(processor.process("rotated", null)).thenThrow(new BadJOSEException("bad key"));
        when(provider.refreshJwkSet()).thenThrow(new IOException("still down"));
        
        assertThrows(AccessException.class, () -> validator.validate("rotated"));
    }
    
    @Test
    void testValidateRetriesWithRefreshedJwksAfterBadJoseFailure() throws Exception {
        JwtTokenValidator validator = newValidator(mockConfig());
        ConfigurableJWTProcessor<SecurityContext> processor = mockProcessor();
        JwksProvider provider = mock(JwksProvider.class);
        ReflectionTestUtils.setField(validator, "jwtProcessor", processor);
        ReflectionTestUtils.setField(validator, "jwksProvider", provider);
        when(processor.process("rotated", null)).thenThrow(new BadJOSEException("bad key"));
        when(provider.refreshJwkSet()).thenReturn(new JWKSet());
        
        assertThrows(AccessException.class, () -> validator.validate("rotated"));
        assertNotNull(ReflectionTestUtils.getField(validator, "jwtProcessor"));
    }
    
    @Test
    void testLazyProcessorInitializationBuildsProcessor() throws Exception {
        OidcAuthConfig config = mockConfig();
        when(config.getIssuerUri()).thenReturn("http://issuer");
        JwtTokenValidator validator = newValidator(config);
        JwksProvider provider = mock(JwksProvider.class);
        when(provider.getJwkSet()).thenReturn(new JWKSet());
        ReflectionTestUtils.setField(validator, "jwksProvider", provider);
        
        Object processor = ReflectionTestUtils.invokeMethod(validator, "getJwtProcessor");
        
        assertNotNull(processor);
    }
    
    @Test
    void testRetryWithRefreshedJwksRebuildsProcessorBeforeFailure() throws Exception {
        JwtTokenValidator validator = newValidator(mockConfig());
        JwksProvider provider = mock(JwksProvider.class);
        when(provider.refreshJwkSet()).thenReturn(new JWKSet());
        ReflectionTestUtils.setField(validator, "jwksProvider", provider);
        
        UndeclaredThrowableException exception = assertThrows(UndeclaredThrowableException.class,
            () -> ReflectionTestUtils.invokeMethod(validator, "retryWithRefreshedJwks",
                "not-a-jwt", new BadJOSEException("bad key")));
        
        assertTrue(exception.getUndeclaredThrowable() instanceof AccessException);
        assertNotNull(ReflectionTestUtils.getField(validator, "jwtProcessor"));
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
        assertFalse(validator.isAdmin(new JWTClaimsSet.Builder()
            .claim("roles", Collections.singletonList("reader")).build()));
    }
    
    @SuppressWarnings("unchecked")
    private ConfigurableJWTProcessor<SecurityContext> mockProcessor() {
        return mock(ConfigurableJWTProcessor.class);
    }
    
    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
            .subject("subject")
            .issuer("http://issuer")
            .expirationTime(new Date(System.currentTimeMillis() + 60_000L))
            .issueTime(new Date(System.currentTimeMillis() - 1_000L))
            .audience("nacos");
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
