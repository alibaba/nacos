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

package com.alibaba.nacos.plugin.auth.impl.controller;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.codec.Base64;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCClient;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCConfigs;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCProvider;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCService;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCState;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SuppressWarnings("checkstyle:abbreviationaswordinname")
@ExtendWith(MockitoExtension.class)
class OIDCControllerTest {
    
    @Mock
    private OIDCClient oidcClient;
    
    @Mock
    private OIDCService oidcService;
    
    @InjectMocks
    private OIDCController oidcController;
    
    private MockHttpServletResponse response;
    
    private MockHttpSession session;
    
    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }
    
    @Test
    void testGetProvider() {
        try (MockedStatic<OIDCConfigs> mockedConfigurations = mockStatic(OIDCConfigs.class)) {
            mockedConfigurations.when(OIDCConfigs::getProvider).thenReturn("test-provider");
            mockedConfigurations.when(() -> OIDCConfigs.getNameByKey("test-provider")).thenReturn("Test Provider");
            
            OIDCProvider provider = oidcController.getProvider();
            
            assertEquals("test-provider", provider.getKey());
            assertEquals("Test Provider", provider.getName());
        }
    }
    
    @Test
    void testStartAuthentication() throws IOException {
        String origin = "http://localhost:8848/nacos/#/login";
        
        String callbackUri = ServletUriComponentsBuilder.fromCurrentContextPath().path(OIDCController.CALLBACK_PATH)
                .toUriString();
        
        AuthenticationRequest authRequest = mock(AuthenticationRequest.class);
        when(authRequest.getState()).thenReturn(new State());
        when(authRequest.getNonce()).thenReturn(new Nonce());
        when(authRequest.toURI()).thenReturn(java.net.URI.create("http://auth-server.com/auth"));
        when(oidcClient.createAuthenticationRequest(callbackUri, origin)).thenReturn(authRequest);
        
        oidcController.startAuthentication(origin, response, session);
        
        assertEquals("http://auth-server.com/auth", response.getRedirectedUrl());
        assertEquals(authRequest.getState().getValue(), session.getAttribute(AuthConstants.OIDC_STATE));
        assertEquals(authRequest.getNonce().getValue(), session.getAttribute(AuthConstants.OIDC_NONCE));
    }
    
    @Test
    void testCallbackWithValidState() throws Exception {
        String origin = "http://localhost:8848/nacos/#/login";
        OIDCState state = new OIDCState();
        state.setOrigin(origin);
        state.setNonce(new Nonce().getValue());
        state.setState(new State().getValue());
        String stateValue = state.toState().getValue();
        session.setAttribute(AuthConstants.OIDC_STATE, stateValue);
        
        UserInfo userInfo = mock(UserInfo.class);
        when(userInfo.getPreferredUsername()).thenReturn("nacos");
        
        NacosUser nacosUser = new NacosUser();
        nacosUser.setUserName("nacos");
        nacosUser.setToken("1234567890");
        nacosUser.setGlobalAdmin(true);
        
        when(oidcClient.getUserInfo(any(), anyString(), anyString())).thenReturn(userInfo);
        when(oidcService.getUser("nacos")).thenReturn(nacosUser);
        
        String code = "authCode";
        oidcController.callback(code, stateValue, response, session);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put(Constants.ACCESS_TOKEN, nacosUser.getToken());
        result.put(Constants.TOKEN_TTL, oidcService.getTokenTtlInSeconds(nacosUser.getToken()));
        result.put(Constants.GLOBAL_ADMIN, nacosUser.isGlobalAdmin());
        result.put(Constants.USERNAME, nacosUser.getUserName());
        
        String token = new String(Base64.encodeBase64(result.toString().getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        String expectedRedirect = "http://localhost:8848/nacos/#/login?token=" + token;
        
        assertEquals(expectedRedirect, response.getRedirectedUrl());
    }
    
    @Test
    void testCallbackWithInvalidState() throws IOException {
        String origin = "http://localhost:8848/nacos/#/login";
        OIDCState state = new OIDCState();
        state.setOrigin(origin);
        state.setNonce(new Nonce().getValue());
        state.setState(new State().getValue());
        String code = "authCode";
        String stateValue = state.toState().getValue();
        session.setAttribute(AuthConstants.OIDC_STATE, stateValue);
        
        oidcController.callback(code, "invalid state", response, session);
        
        String expectedRedirect = "http://localhost:8848/nacos/#/login?msg=Invalid state";
        assertEquals(expectedRedirect, response.getRedirectedUrl());
    }
    
}
