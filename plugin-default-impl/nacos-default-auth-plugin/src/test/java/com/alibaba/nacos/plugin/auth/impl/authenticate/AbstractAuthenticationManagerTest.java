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

package com.alibaba.nacos.plugin.auth.impl.authenticate;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AbstractAuthenticationManagerTest {
    
    @InjectMocks
    private AbstractAuthenticationManager abstractAuthenticationManager;
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private TokenManagerDelegate jwtTokenManager;
    
    @Mock
    private NacosRoleServiceImpl roleService;
    
    private User user;
    
    @BeforeEach
    void setUp() throws Exception {
        user = new User();
        user.setUsername("nacos");
        user.setPassword(PasswordEncoderUtil.encode("test"));
    }
    
    @Test
    void testAuthenticate1() {
        assertThrows(AccessException.class, () -> {
            abstractAuthenticationManager.authenticate(null, "pwd");
        });
    }
    
    @Test
    void testAuthenticate2() {
        assertThrows(AccessException.class, () -> {
            abstractAuthenticationManager.authenticate("nacos", null);
        });
    }
    
    @Test
    void testAuthenticate3() throws AccessException {
        NacosUserDetails nacosUserDetails = new NacosUserDetails(user);
        
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(nacosUserDetails);
        
        when(jwtTokenManager.createToken(anyString())).thenReturn("token");
        
        NacosUser nacosUser = abstractAuthenticationManager.authenticate("nacos", "test");
        
        assertEquals("token", nacosUser.getToken());
        assertEquals(user.getUsername(), nacosUser.getUserName());
    }
    
    @Test
    void testAuthenticate4() {
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(null);
        
        assertThrows(AccessException.class, () -> {
            abstractAuthenticationManager.authenticate("nacos", "test");
        });
    }
    
    @Test
    void testAuthenticate5() {
        assertThrows(AccessException.class, () -> {
            abstractAuthenticationManager.authenticate("");
        });
    }
    
    @Test
    void testAuthenticate6() throws AccessException {
        NacosUser nacosUser = new NacosUser();
        
        when(jwtTokenManager.parseToken(anyString())).thenReturn(nacosUser);
        NacosUser authenticate = abstractAuthenticationManager.authenticate("token");
        
        assertEquals(nacosUser, authenticate);
    }
    
    @Test
    void testAuthenticate7() throws AccessException {
        NacosUser nacosUser = new NacosUser();
        when(jwtTokenManager.parseToken(anyString())).thenReturn(nacosUser);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.TOKEN_PREFIX + "-token");
        NacosUser authenticate = abstractAuthenticationManager.authenticate(mockHttpServletRequest);
        
        assertEquals(nacosUser, authenticate);
    }
    
    @Test
    void testAuthenticate8() throws AccessException {
        NacosUser nacosUser = new NacosUser();
        when(jwtTokenManager.parseToken(anyString())).thenReturn(nacosUser);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.addHeader(AuthConstants.AUTHORIZATION_HEADER, "token");
        mockHttpServletRequest.addParameter(Constants.ACCESS_TOKEN, "token");
        NacosUser authenticate = abstractAuthenticationManager.authenticate(mockHttpServletRequest);
        
        assertEquals(nacosUser, authenticate);
    }
    
    @Test
    void testAuthenticate9() throws AccessException {
        NacosUserDetails nacosUserDetails = new NacosUserDetails(user);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(nacosUserDetails);
        
        when(jwtTokenManager.createToken(anyString())).thenReturn("token");
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.addHeader(AuthConstants.AUTHORIZATION_HEADER, "token");
        mockHttpServletRequest.addParameter(AuthConstants.PARAM_USERNAME, "nacos");
        mockHttpServletRequest.addParameter(AuthConstants.PARAM_PASSWORD, "test");
        NacosUser authenticate = abstractAuthenticationManager.authenticate(mockHttpServletRequest);
        
        assertEquals("token", authenticate.getToken());
        assertEquals(user.getUsername(), authenticate.getUserName());
    }
    
    @Test
    void testAuthorize() {
        Permission permission = new Permission();
        NacosUser nacosUser = new NacosUser();
        when(roleService.hasPermission(nacosUser, permission)).thenReturn(false);
        
        assertThrows(AccessException.class, () -> {
            abstractAuthenticationManager.authorize(permission, nacosUser);
        });
    }
    
    @Test
    void testHasGlobalAdminRole() {
        when(roleService.hasGlobalAdminRole(anyString())).thenReturn(true);
        
        boolean hasGlobalAdminRole = abstractAuthenticationManager.hasGlobalAdminRole("nacos");
        
        assertTrue(hasGlobalAdminRole);
    }
    
    @Test
    void testHasGlobalAdminRole2() {
        when(roleService.hasGlobalAdminRole()).thenReturn(true);
        
        boolean hasGlobalAdminRole = abstractAuthenticationManager.hasGlobalAdminRole();
        
        assertTrue(hasGlobalAdminRole);
    }
    
    @Test
    void testHasGlobalAdminRole3() {
        NacosUser nacosUser = new NacosUser("nacos");
        nacosUser.setGlobalAdmin(true);
        
        boolean hasGlobalAdminRole = abstractAuthenticationManager.hasGlobalAdminRole(nacosUser);
        
        assertTrue(hasGlobalAdminRole);
    }
    
    @Test
    void testHasGlobalAdminRole4() {
        NacosUser nacosUser = new NacosUser("nacos");
        nacosUser.setGlobalAdmin(false);
        when(roleService.hasGlobalAdminRole(anyString())).thenReturn(true);
        boolean hasGlobalAdminRole = abstractAuthenticationManager.hasGlobalAdminRole(nacosUser);
        
        assertTrue(hasGlobalAdminRole);
    }
}
