/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("checkstyle:abbreviationaswordinname")
class OIDCClientTest {
    
    private OIDCConfig config;
    
    private OIDCProviderMetadata providerMetadata;
    
    private OIDCClient oidcClient;
    
    @BeforeEach
    void setUp() {
        config = Mockito.mock(OIDCConfig.class);
        when(config.getIssuerUri()).thenReturn("http://auth-server.com");
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getScope()).thenReturn("openid profile");
        oidcClient = Mockito.spy(new OIDCClient(config));
        providerMetadata = Mockito.mock(OIDCProviderMetadata.class);
        doReturn(providerMetadata).when(oidcClient).getProviderMetadata();
    }
    
    @Test
    void testCreateAuthenticationRequest() {
        String callbackUrl = "http://localhost:8080/callback";
        when(providerMetadata.getAuthorizationEndpointURI()).thenReturn(URI.create("http://auth-server.com/auth"));
        
        AuthenticationRequest authRequest = oidcClient.createAuthenticationRequest(callbackUrl, "");
        
        assertNotNull(authRequest);
        assertEquals("http://auth-server.com/auth", authRequest.getEndpointURI().toString());
        assertEquals("test-client-id", authRequest.getClientID().getValue());
        assertEquals("openid profile", authRequest.getScope().toString());
    }
    
    @Test
    void testGetUserInfo() {
        OIDCTokens oidcTokens = mock(OIDCTokens.class);
        
        UserInfo mockUserInfo = new UserInfo(new Subject("test-user-id"));
        mockUserInfo.setName("test-user");
        
        Nonce nonce = new Nonce();
        UserInfoResponse resp = new UserInfoSuccessResponse(mockUserInfo);
        AuthorizationCode authorizationCode = new AuthorizationCode("test-code");
        String callbackUrl = "http://localhost:8080/callback";
        
        doReturn(oidcTokens).when(oidcClient).exchangeToken(authorizationCode, callbackUrl, nonce.getValue());
        doReturn(resp).when(oidcClient).exchangeUserInfo(any(), any());
        
        UserInfo userInfo = oidcClient.getUserInfo(authorizationCode, callbackUrl, nonce.getValue());
        
        assertNotNull(userInfo);
        assertEquals("test-user", userInfo.getName());
        
        verify(oidcClient, times(1)).exchangeUserInfo(any(), any());
    }
    
    @Test
    void testGetUserInfoFromJwt() throws ParseException, java.text.ParseException {
     
        OIDCTokens oidcTokens = mock(OIDCTokens.class);
        JWT jwt = mock(JWT.class);
        UserInfo mockUserInfo = new UserInfo(new Subject("test-user-id"));
        mockUserInfo.setName("test-user");
        doReturn(jwt).when(oidcTokens).getIDToken();
        doReturn(mockUserInfo.toJWTClaimsSet()).when(jwt).getJWTClaimsSet();
        Nonce nonce = new Nonce();
        
        AuthorizationCode authorizationCode = new AuthorizationCode("test-code");
        String callbackUrl = "http://localhost:8080/callback";
        doReturn(oidcTokens).when(oidcClient).exchangeToken(authorizationCode, callbackUrl, nonce.getValue());
        
        UserInfo userInfo = oidcClient.getUserInfo(authorizationCode, callbackUrl, nonce.getValue());
        
        assertNotNull(userInfo);
        assertEquals("test-user", userInfo.getName());
        
        verify(oidcClient, never()).exchangeUserInfo(any(), any());
    }
}
