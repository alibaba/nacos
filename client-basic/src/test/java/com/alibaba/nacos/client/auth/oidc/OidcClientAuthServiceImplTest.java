/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.oidc;

import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link OidcClientAuthServiceImpl}.
 *
 * @author wangzji
 */
class OidcClientAuthServiceImplTest {
    
    private OidcClientAuthServiceImpl oidcClientAuthService;
    
    @BeforeEach
    void setUp() {
        oidcClientAuthService = new OidcClientAuthServiceImpl();
    }
    
    @Test
    void testLoginWithoutOidcConfig() {
        // Given: no OIDC properties configured
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        properties.setProperty("username", "nacos");
        properties.setProperty("password", "nacos");
        
        // When
        boolean result = oidcClientAuthService.login(properties);
        
        // Then: should succeed silently (OIDC not configured, let other plugins handle)
        assertTrue(result);
        
        // LoginIdentityContext should not contain accessToken
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNotNull(ctx);
        assertNull(ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
    }
    
    @Test
    void testLoginWithoutOidcConfigSkipsOnSubsequentCalls() {
        // Given: no OIDC properties configured
        Properties properties = new Properties();
        
        // When: call login multiple times
        boolean result1 = oidcClientAuthService.login(properties);
        boolean result2 = oidcClientAuthService.login(properties);
        
        // Then: both should succeed
        assertTrue(result1);
        assertTrue(result2);
    }
    
    @Test
    void testLoginWithPartialOidcConfigMissingSecret() {
        // Given: only client-id but no client-secret
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, "https://idp.example.com");
        
        // When
        boolean result = oidcClientAuthService.login(properties);
        
        // Then: should succeed (incomplete OIDC config, skip)
        assertTrue(result);
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNull(ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
    }
    
    @Test
    void testLoginWithPartialOidcConfigMissingEndpoint() {
        // Given: client-id and secret but no issuer-uri or token-endpoint
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        // When
        boolean result = oidcClientAuthService.login(properties);
        
        // Then: should succeed (incomplete OIDC config, skip)
        assertTrue(result);
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNull(ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
    }
    
    @Test
    void testGetLoginIdentityContextReturnsEmptyByDefault() {
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNotNull(ctx);
    }
}
