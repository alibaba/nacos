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

import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationClient;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationRequest;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationResponse;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcAuthenticationManagerTest {
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(OidcAuthenticationManager.class, "instance", null);
    }
    
    @Test
    void testAuthenticateTokenMapsClaimsToUser() throws AccessException {
        JwtTokenValidator tokenValidator = mock(JwtTokenValidator.class);
        OidcUserMapper userMapper = mock(OidcUserMapper.class);
        OidcAuthenticationManager manager = newManager(tokenValidator, userMapper);
        JWTClaimsSet claims = claims();
        OidcUser user = new OidcUser();
        user.setUsername("nacos");
        when(tokenValidator.validate("token")).thenReturn(claims);
        when(userMapper.mapToUser(claims)).thenReturn(user);
        
        OidcUser actual = manager.authenticate("token");
        
        assertSame(user, actual);
        assertEquals("token", actual.getToken());
    }
    
    @Test
    void testAuthenticateTokenRejectsBlankToken() {
        OidcAuthenticationManager manager =
            newManager(mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
        
        assertThrows(AccessException.class, () -> manager.authenticate(" "));
    }
    
    @Test
    void testAuthenticateIdentityContextUsesBearerToken() throws AccessException {
        JwtTokenValidator tokenValidator = mock(JwtTokenValidator.class);
        OidcUserMapper userMapper = mock(OidcUserMapper.class);
        OidcAuthenticationManager manager = newManager(tokenValidator, userMapper);
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(OidcProtocolConstants.AUTHORIZATION_HEADER,
            OidcProtocolConstants.BEARER_PREFIX + "bearer-token");
        JWTClaimsSet claims = claims();
        OidcUser user = new OidcUser();
        user.setUsername("nacos");
        when(tokenValidator.validate("bearer-token")).thenReturn(claims);
        when(userMapper.mapToUser(claims)).thenReturn(user);
        
        OidcUser actual = manager.authenticate(identityContext);
        
        assertSame(user, actual);
        assertEquals("bearer-token", actual.getToken());
    }
    
    @Test
    void testAuthenticateIdentityContextFallsBackToAccessTokenParameter() throws AccessException {
        JwtTokenValidator tokenValidator = mock(JwtTokenValidator.class);
        OidcUserMapper userMapper = mock(OidcUserMapper.class);
        OidcAuthenticationManager manager = newManager(tokenValidator, userMapper);
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM, "param-token");
        JWTClaimsSet claims = claims();
        OidcUser user = new OidcUser();
        user.setUsername("nacos");
        when(tokenValidator.validate("param-token")).thenReturn(claims);
        when(userMapper.mapToUser(claims)).thenReturn(user);
        
        OidcUser actual = manager.authenticate(identityContext);
        
        assertSame(user, actual);
        assertEquals("param-token", actual.getToken());
    }
    
    @Test
    void testAuthenticateIdentityContextRejectsMissingToken() {
        OidcAuthenticationManager manager =
            newManager(mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
        
        assertThrows(AccessException.class, () -> manager.authenticate(new IdentityContext()));
    }
    
    @Test
    void testUserContextHelpersAndGlobalAdminCheck() {
        OidcAuthenticationManager manager =
            newManager(mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
        IdentityContext identityContext = new IdentityContext();
        OidcUser user = new OidcUser();
        user.setGlobalAdmin(true);
        
        manager.setUserInContext(identityContext, user);
        
        assertSame(user, manager.getUserFromContext(identityContext));
        assertTrue(manager.isGlobalAdmin(user));
        assertFalse(manager.isGlobalAdmin(null));
        identityContext.setParameter(OidcConstants.OAUTH2_USER_KEY, "not-user");
        assertNull(manager.getUserFromContext(identityContext));
    }
    
    @Test
    void testHasPermissionReturnsFalseForMissingUser() {
        OidcAuthenticationManager manager =
            newManager(mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
        
        assertFalse(manager.hasPermission(null, permission()));
    }
    
    @Test
    void testHasPermissionDelegatesToAuthorizationClientForAllowedResponse() {
        OidcAuthenticationManager manager =
            newManager(mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
        AuthorizationClient client = mock(AuthorizationClient.class);
        OidcUser user = user();
        Permission permission = permission();
        when(client.authorize(any(AuthorizationRequest.class))).thenReturn(
            AuthorizationResponse.allowed());
        
        try (MockedStatic<AuthorizationClient> clientStatic =
            mockStatic(AuthorizationClient.class)) {
            clientStatic.when(AuthorizationClient::getInstance).thenReturn(client);
            
            assertTrue(manager.hasPermission(user, permission));
        }
        
        ArgumentCaptor<AuthorizationRequest> captor =
            ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(client).authorize(captor.capture());
        assertEquals("token", captor.getValue().getToken());
        assertEquals("nacos:config:public:DEFAULT_GROUP:data.yaml",
            captor.getValue().buildResourceUri());
    }
    
    @Test
    void testHasPermissionReturnsFalseForDeniedResponse() {
        OidcAuthenticationManager manager =
            newManager(mock(JwtTokenValidator.class), mock(OidcUserMapper.class));
        AuthorizationClient client = mock(AuthorizationClient.class);
        when(client.authorize(any(AuthorizationRequest.class))).thenReturn(
            AuthorizationResponse.denied("blocked"));
        
        try (MockedStatic<AuthorizationClient> clientStatic =
            mockStatic(AuthorizationClient.class)) {
            clientStatic.when(AuthorizationClient::getInstance).thenReturn(client);
            
            assertFalse(manager.hasPermission(user(), permission()));
        }
    }
    
    private OidcAuthenticationManager newManager(JwtTokenValidator tokenValidator,
        OidcUserMapper userMapper) {
        ReflectionTestUtils.setField(OidcAuthenticationManager.class, "instance", null);
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class);
            MockedStatic<JwtTokenValidator> validatorStatic =
                mockStatic(JwtTokenValidator.class);
            MockedStatic<OidcUserMapper> mapperStatic = mockStatic(OidcUserMapper.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            validatorStatic.when(JwtTokenValidator::getInstance).thenReturn(tokenValidator);
            mapperStatic.when(OidcUserMapper::getInstance).thenReturn(userMapper);
            return OidcAuthenticationManager.getInstance();
        }
    }
    
    private JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder()
            .subject("subject")
            .issuer("issuer")
            .expirationTime(new Date(System.currentTimeMillis() + 60000L))
            .issueTime(new Date())
            .build();
    }
    
    private OidcUser user() {
        OidcUser user = new OidcUser();
        user.setUsername("nacos");
        user.setToken("token");
        return user;
    }
    
    private Permission permission() {
        Resource resource = new Resource("public", "DEFAULT_GROUP", "data.yaml", "config", null);
        return new Permission(resource, "read");
    }
}
