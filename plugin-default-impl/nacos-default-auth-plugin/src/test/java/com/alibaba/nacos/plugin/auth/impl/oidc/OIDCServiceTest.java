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
import static org.mockito.Mockito.mock;
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
    void testGetUserFromNacos() throws AccessException {
        String username = "nacos";
        String token = "test-token";
        
        NacosUserDetails userDetails = mock(NacosUserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(jwtTokenManager.createToken(username)).thenReturn(token);
        
        NacosUser user = oidcService.getUser(username);
        
        assertNotNull(user);
        assertEquals(username, user.getUserName());
        assertEquals(token, user.getToken());
    }
    
    @Test
    void testGetUserWithLdapFallback() throws AccessException {
        String username = "ldapUser";
        String ldapUsername = AuthConstants.OIDC_PREFIX + username;
        String token = "ldap-token";
        
        when(userDetailsService.loadUserByUsername(username)).thenThrow(
                new UsernameNotFoundException("User not found"));
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username)).thenThrow(
                new UsernameNotFoundException("LDAP user not found"));
        doNothing().when(userDetailsService).createUser(eq(ldapUsername), anyString());
        when(jwtTokenManager.createToken(ldapUsername)).thenReturn(token);
        
        NacosUser user = oidcService.getUser(username);
        
        assertNotNull(user);
        assertEquals(ldapUsername, user.getUserName());
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
