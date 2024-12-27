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

import com.alibaba.nacos.plugin.auth.exception.AccessException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.LdapTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LdapAuthenticationManagerTest {
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private TokenManagerDelegate jwtTokenManager;
    
    @Mock
    private NacosRoleServiceImpl roleService;
    
    @Mock
    private LdapTemplate ldapTemplate;
    
    private LdapAuthenticationManager ldapAuthenticationManager;
    
    private User user;
    
    @BeforeEach
    void setUp() throws Exception {
        user = new User();
        user.setUsername("nacos");
        user.setPassword(PasswordEncoderUtil.encode("test"));
        ldapAuthenticationManager = new LdapAuthenticationManager(ldapTemplate, userDetailsService, jwtTokenManager,
                roleService, "", true);
    }
    
    @Test
    void testLdapAuthenticate() throws AccessException {
        NacosUserDetails nacosUserDetails = new NacosUserDetails(user);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(nacosUserDetails);
        NacosUser authenticate = ldapAuthenticationManager.authenticate("nacos", "test");
        assertEquals(user.getUsername(), authenticate.getUserName());
    }
}
