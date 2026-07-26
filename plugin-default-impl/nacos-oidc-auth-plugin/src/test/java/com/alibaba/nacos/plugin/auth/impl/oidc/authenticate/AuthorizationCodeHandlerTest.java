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
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadata;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadataProvider;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationCodeHandlerTest {
    
    @Test
    void testBuildAuthorizationUrlRejectsMissingEndpoint() throws Exception {
        AuthorizationCodeHandler handler = newHandler(mockConfig(), metadataProvider(null,
            null, null));
        
        assertThrows(AccessException.class,
            () -> handler.buildAuthorizationUrl("http://nacos/callback"));
    }
    
    @Test
    void testBuildAuthorizationUrlWrapsInvalidEndpoint() throws Exception {
        AuthorizationCodeHandler handler = newHandler(mockConfig(),
            metadataProvider("://bad-endpoint", null, null));
        
        assertThrows(AccessException.class,
            () -> handler.buildAuthorizationUrl("http://nacos/callback"));
    }
    
    @Test
    void testBuildAuthorizationUrlIncludesOidcParameters() throws AccessException {
        OidcAuthPluginConfig config = mockConfig();
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
    void testBuildLogoutUrlReturnsNullWhenEndpointMissing() throws Exception {
        AuthorizationCodeHandler handler = newHandler(mockConfig(), metadataProvider(
            "http://idp/authorize", null, null));
        
        assertNull(handler.buildLogoutUrl("id-token", "http://nacos"));
    }
    
    @Test
    void testBuildLogoutUrlWithOptionalParameters() {
        OidcAuthPluginConfig config = mockConfig();
        AuthorizationCodeHandler handler = newHandler(config);
        
        String logoutUrl = handler.buildLogoutUrl("id-token", "http://nacos");
        
        assertTrue(logoutUrl.startsWith("http://idp/logout?"));
        assertTrue(logoutUrl.contains("id_token_hint=id-token"));
        assertTrue(logoutUrl.contains("post_logout_redirect_uri=http://nacos"));
        assertTrue(logoutUrl.contains("client_id=client"));
    }
    
    @Test
    void testBuildLogoutUrlWithClientOnly() {
        OidcAuthPluginConfig config = mockConfig();
        AuthorizationCodeHandler handler = newHandler(config);
        
        String logoutUrl = handler.buildLogoutUrl("", "");
        
        assertTrue(logoutUrl.startsWith("http://idp/logout?"));
        assertTrue(logoutUrl.endsWith("&client_id=client"));
    }
    
    @Test
    void testBuildLogoutUrlWithRedirectOnly() {
        OidcAuthPluginConfig config = mockConfig();
        AuthorizationCodeHandler handler = newHandler(config);
        
        String logoutUrl = handler.buildLogoutUrl("", "http://nacos");
        
        assertEquals("http://idp/logout?post_logout_redirect_uri=http://nacos&client_id=client",
            logoutUrl);
    }
    
    @Test
    void testSignedStateRoundTripAndInvalidBranches() {
        AuthorizationCodeHandler handler = newHandler(mockConfig());
        long future = System.currentTimeMillis() + 60_000L;
        long past = System.currentTimeMillis() - 1_000L;
        
        String state = ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
            future);
        Object stateData = ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState",
            state);
        
        assertNotNull(stateData);
        assertEquals("nonce", ReflectionTestUtils.getField(stateData, "nonce"));
        assertEquals(future, ReflectionTestUtils.getField(stateData, "expirationTime"));
        assertNull(ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState",
            encodeState("only.two")));
        assertNull(ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState",
            encodeState("nonce.not-number.signature")));
        assertNull(ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState",
            encodeState("nonce." + future + ".bad-signature")));
        String expiredState = ReflectionTestUtils.invokeMethod(handler, "buildSignedState",
            "nonce", past);
        assertNull(ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState",
            expiredState));
        assertNull(ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState", "%%"));
    }
    
    @Test
    void testVerifyAndDecodeStateCatchesSigningFailure() {
        OidcAuthPluginConfig config = configWithSecret("");
        AuthorizationCodeHandler handler = newHandler(config);
        long future = System.currentTimeMillis() + 60_000L;
        
        assertNull(ReflectionTestUtils.invokeMethod(handler, "verifyAndDecodeState",
            encodeState("nonce." + future + ".signature")));
    }
    
    @Test
    void testBuildSignedStateRequiresClientSecret() {
        OidcAuthPluginConfig config = configWithSecret("");
        AuthorizationCodeHandler handler = newHandler(config);
        
        assertThrows(RuntimeException.class,
            () -> ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
                System.currentTimeMillis() + 60_000L));
    }
    
    @Test
    void testExchangeCodeRejectsInvalidStateAndMissingTokenEndpoint() {
        AuthorizationCodeHandler handler = newHandler(mockConfig());
        assertThrows(AccessException.class,
            () -> handler.exchangeCodeForUser("code", "bad-state", "http://nacos/callback"));
        
        String state = ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
            System.currentTimeMillis() + 60_000L);
        assertThrows(AccessException.class,
            () -> handler.exchangeCodeForUser("code", state, "http://nacos/callback"));
    }
    
    @Test
    void testExchangeCodeMapsUserAndAccessToken() throws Exception {
        String idToken = plainJwt();
        HttpServer server = startTokenServer(200, tokenSuccessBody(idToken));
        try {
            JwtTokenValidator validator = mock(JwtTokenValidator.class);
            OidcUserMapper mapper = mock(OidcUserMapper.class);
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("subject")
                .claim("nonce", "nonce").build();
            OidcUser user = new OidcUser();
            user.setUsername("nacos");
            when(validator.validate(idToken)).thenReturn(claims);
            when(mapper.mapToUser(claims)).thenReturn(user);
            AuthorizationCodeHandler handler = tokenHandler(server, true, validator, mapper);
            String state = ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
                System.currentTimeMillis() + 60_000L);
            
            OidcUser result = handler.exchangeCodeForUser("code", state, "http://nacos/callback");
            
            assertEquals("nacos", result.getUsername());
            assertEquals("access-token", result.getToken());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testExchangeCodeHandlesNonceValidationBranches() throws Exception {
        String idToken = plainJwt();
        JWTClaimsSet claimsWithoutNonce = new JWTClaimsSet.Builder().subject("subject").build();
        JWTClaimsSet mismatchClaims = new JWTClaimsSet.Builder().subject("subject")
            .claim("nonce", "other").build();
        
        assertThrows(AccessException.class,
            () -> exchangeWithClaims(idToken, claimsWithoutNonce, true));
        assertEquals("access-token", exchangeWithClaims(idToken, claimsWithoutNonce, false)
            .getToken());
        assertThrows(AccessException.class,
            () -> exchangeWithClaims(idToken, mismatchClaims, true));
    }
    
    @Test
    void testExchangeCodeRejectsTokenEndpointErrorResponse() throws Exception {
        HttpServer server = startTokenServer(400,
            "{\"error\":\"invalid_grant\",\"error_description\":\"bad code\"}");
        try {
            AuthorizationCodeHandler handler = tokenHandler(server, true,
                mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
            String state = ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
                System.currentTimeMillis() + 60_000L);
            
            assertThrows(AccessException.class,
                () -> handler.exchangeCodeForUser("code", state, "http://nacos/callback"));
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testExchangeCodeWrapsUnexpectedTokenValidationFailure() throws Exception {
        String idToken = plainJwt();
        HttpServer server = startTokenServer(200, tokenSuccessBody(idToken));
        try {
            JwtTokenValidator validator = mock(JwtTokenValidator.class);
            when(validator.validate(idToken)).thenThrow(new IllegalStateException("broken"));
            AuthorizationCodeHandler handler = tokenHandler(server, true, validator,
                mock(OidcUserMapper.class));
            String state = ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
                System.currentTimeMillis() + 60_000L);
            
            AccessException exception = assertThrows(AccessException.class,
                () -> handler.exchangeCodeForUser("code", state, "http://nacos/callback"));
            
            assertTrue(exception.getErrMsg().contains("Authentication failed"));
        } finally {
            server.stop(0);
        }
    }
    
    private AuthorizationCodeHandler newHandler(OidcAuthPluginConfig config) {
        try {
            return newHandler(config, metadataProvider("http://idp/authorize", null,
                "http://idp/logout"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    private AuthorizationCodeHandler newHandler(OidcAuthPluginConfig config,
        OidcProviderMetadataProvider metadataProvider) {
        return newHandler(config, metadataProvider, mock(JwtTokenValidator.class),
            mock(OidcUserMapper.class));
    }
    
    private AuthorizationCodeHandler newHandler(OidcAuthPluginConfig config,
        JwtTokenValidator validator, OidcUserMapper mapper) {
        try {
            return newHandler(config, metadataProvider("http://idp/authorize", null,
                "http://idp/logout"), validator, mapper);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    private AuthorizationCodeHandler newHandler(OidcAuthPluginConfig config,
        OidcProviderMetadataProvider metadataProvider, JwtTokenValidator validator,
        OidcUserMapper mapper) {
        return new AuthorizationCodeHandler(config, metadataProvider, validator, mapper);
    }
    
    private OidcAuthPluginConfig mockConfig() {
        return configWithSecret("secret");
    }
    
    private OidcAuthPluginConfig configWithSecret(String secret) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put(OidcAuthPluginConfig.CLIENT_ID, "client");
        config.put(OidcAuthPluginConfig.CLIENT_SECRET, secret);
        config.put(OidcAuthPluginConfig.SCOPE, "openid profile");
        return OidcAuthPluginConfig.from(config);
    }
    
    private String encodeState(String stateContent) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(stateContent.getBytes(StandardCharsets.UTF_8));
    }
    
    private OidcUser exchangeWithClaims(String idToken, JWTClaimsSet claims, boolean strictNonce)
        throws Exception {
        HttpServer server = startTokenServer(200, tokenSuccessBody(idToken));
        try {
            JwtTokenValidator validator = mock(JwtTokenValidator.class);
            OidcUserMapper mapper = mock(OidcUserMapper.class);
            OidcUser user = new OidcUser();
            user.setUsername("nacos");
            when(validator.validate(idToken)).thenReturn(claims);
            when(mapper.mapToUser(claims)).thenReturn(user);
            AuthorizationCodeHandler handler = tokenHandler(server, strictNonce, validator,
                mapper);
            String state = ReflectionTestUtils.invokeMethod(handler, "buildSignedState", "nonce",
                System.currentTimeMillis() + 60_000L);
            return handler.exchangeCodeForUser("code", state, "http://nacos/callback");
        } finally {
            server.stop(0);
        }
    }
    
    private AuthorizationCodeHandler tokenHandler(HttpServer server, boolean strictNonce,
        JwtTokenValidator validator, OidcUserMapper mapper) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(OidcAuthPluginConfig.CLIENT_ID, "client");
        values.put(OidcAuthPluginConfig.CLIENT_SECRET, "secret");
        values.put(OidcAuthPluginConfig.SCOPE, "openid profile");
        values.put(OidcAuthPluginConfig.STRICT_NONCE_VALIDATION,
            Boolean.toString(strictNonce));
        OidcAuthPluginConfig config = OidcAuthPluginConfig.from(values);
        String tokenEndpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
        return newHandler(config, metadataProvider("http://idp/authorize", tokenEndpoint,
            "http://idp/logout"), validator, mapper);
    }
    
    private OidcProviderMetadataProvider metadataProvider(String authorizationEndpoint,
        String tokenEndpoint, String endSessionEndpoint) throws Exception {
        OidcProviderMetadataProvider provider = mock(OidcProviderMetadataProvider.class);
        when(provider.getMetadata()).thenReturn(new OidcProviderMetadata(
            authorizationEndpoint, tokenEndpoint, null, endSessionEndpoint, null));
        return provider;
    }
    
    private HttpServer startTokenServer(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
    
    private String tokenSuccessBody(String idToken) {
        return "{\"access_token\":\"access-token\",\"token_type\":\"Bearer\",\"id_token\":\""
            + idToken + "\"}";
    }
    
    private String plainJwt() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("subject").build();
        return new PlainJWT(claims).serialize();
    }
}
