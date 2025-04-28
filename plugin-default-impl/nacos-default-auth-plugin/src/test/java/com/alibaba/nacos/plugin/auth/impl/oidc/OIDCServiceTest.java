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

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("checkstyle:abbreviationaswordinname")
class OIDCServiceTest {
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private TokenManagerDelegate jwtTokenManager;
    
    @InjectMocks
    private OIDCService oidcService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testGetUserWithUserExist() throws AccessException {
        String username = "nacos";
        User user = new User();
        user.setUsername(username);
        
        when(userDetailsService.loadUserByUsername(username)).thenReturn(new NacosUserDetails(user));
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        when(userDetailsService.loadUserByUsername(AuthConstants.OIDC_PREFIX + username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        doNothing().when(userDetailsService).createUser(anyString(), anyString());
        String token = "test-token";
        when(jwtTokenManager.createToken(anyString())).thenReturn(token);
        
        NacosUser retUser = oidcService.getUser(username);
        
        assertNotNull(retUser);
        assertEquals(user.getUsername(), retUser.getUserName());
        assertEquals(token, retUser.getToken());
        
        verify(jwtTokenManager, times(1)).createToken(retUser.getUserName());
        verify(userDetailsService, never()).createUser(anyString(), anyString());
        verify(userDetailsService, never()).loadUserByUsername(eq(AuthConstants.LDAP_PREFIX + username));
        verify(userDetailsService, never()).loadUserByUsername(eq(AuthConstants.OIDC_PREFIX + username));
    }
    
    @Test
    void testGetUserWithLDAPExist() throws AccessException {
        String username = "nacos";
        User user = new User();
        user.setUsername(AuthConstants.LDAP_PREFIX + username);
        
        when(userDetailsService.loadUserByUsername(username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username)).thenReturn(
                new NacosUserDetails(user));
        when(userDetailsService.loadUserByUsername(AuthConstants.OIDC_PREFIX + username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        doNothing().when(userDetailsService).createUser(anyString(), anyString());
        String token = "test-token";
        when(jwtTokenManager.createToken(anyString())).thenReturn(token);
        
        NacosUser retUser = oidcService.getUser(username);
        
        assertNotNull(retUser);
        assertEquals(user.getUsername(), retUser.getUserName());
        assertEquals(token, retUser.getToken());
        
        verify(jwtTokenManager, times(1)).createToken(retUser.getUserName());
        verify(userDetailsService, never()).createUser(anyString(), anyString());
        verify(userDetailsService, never()).loadUserByUsername(eq(AuthConstants.OIDC_PREFIX + username));
    }
    
    @Test
    void testGetUserWithOIDCExist() throws AccessException {
        String username = "nacos";
        User user = new User();
        user.setUsername(AuthConstants.OIDC_PREFIX + username);
        
        when(userDetailsService.loadUserByUsername(username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username)).thenThrow(
                new UsernameNotFoundException("LDAP user not found"));
        when(userDetailsService.loadUserByUsername(AuthConstants.OIDC_PREFIX + username)).thenReturn(
                new NacosUserDetails(user));
        doNothing().when(userDetailsService).createUser(anyString(), anyString());
        String token = "test-token";
        when(jwtTokenManager.createToken(anyString())).thenReturn(token);
        
        NacosUser retUser = oidcService.getUser(username);
        
        assertNotNull(retUser);
        assertEquals(user.getUsername(), retUser.getUserName());
        assertEquals(token, retUser.getToken());
        
        verify(jwtTokenManager, times(1)).createToken(retUser.getUserName());
        verify(userDetailsService, never()).createUser(anyString(), anyString());
    }
    
    @Test
    void testGetUserWithNotUserFallback() throws AccessException {
        String username = "oidcUser";
        String oidcUsername = AuthConstants.OIDC_PREFIX + username;
        
        when(userDetailsService.loadUserByUsername(username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username)).thenThrow(
                new UsernameNotFoundException("LDAP user not found"));
        when(userDetailsService.loadUserByUsername(oidcUsername)).thenThrow(
                new UsernameNotFoundException("OIDC user not found"));
        doNothing().when(userDetailsService).createUser(eq(oidcUsername), anyString());
        String token = "oidc-token";
        when(jwtTokenManager.createToken(oidcUsername)).thenReturn(token);
        
        NacosUser user = oidcService.getUser(username);
        
        assertNotNull(user);
        assertEquals(oidcUsername, user.getUserName());
        assertEquals(token, user.getToken());
    }
    
    @Test
    void testGetTokenTtlInSeconds() throws AccessException {
        String token = "test-token";
        long expectedTtl = 3600L;
        
        when(jwtTokenManager.getTokenTtlInSeconds(token)).thenReturn(expectedTtl);
        
        long ttl = oidcService.getTokenTtlInSeconds(token);
        
        assertEquals(expectedTtl, ttl);
    }
    
    @Test
    void testGetTokenTtlInSecondsWithException() throws AccessException {
        String token = "invalid-token";
        
        when(jwtTokenManager.getTokenTtlInSeconds(token)).thenThrow(new AccessException("Token not valid"));
        
        long ttl = oidcService.getTokenTtlInSeconds(token);
        
        assertEquals(18000L, ttl);
    }
}
