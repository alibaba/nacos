/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class LdapAuthenticationProviderTest {
    
    private static final String LDAP_PREFIX = "LDAP_";
    
    private final String adminUserName = "nacos";
    
    private final String normalUserName = "normal";
    
    private final String filterPrefix = "uid";
    
    private final boolean caseSensitive = true;
    
    Method isAdmin;
    
    Method ldapLogin;
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private NacosRoleServiceImpl nacosRoleService;
    
    @Mock
    private LdapTemplate ldapTemplate;
    
    private LdapAuthenticationProvider ldapAuthenticationProvider;
    
    private LdapAuthenticationProvider ldapAuthenticationProviderForCloseCaseSensitive;
    
    private List<RoleInfo> roleInfos = new ArrayList<>();
    
    private String defaultPassWord = System.getProperty("ldap.default.password", "nacos");
    
    @BeforeEach
    void setUp() throws NoSuchMethodException {
        RoleInfo adminRole = new RoleInfo();
        adminRole.setRole(AuthConstants.GLOBAL_ADMIN_ROLE);
        adminRole.setUsername(adminUserName);
        roleInfos.add(adminRole);
        when(nacosRoleService.getRoles(adminUserName)).thenReturn(roleInfos);
        when(nacosRoleService.getRoles(normalUserName)).thenReturn(new ArrayList<>());
        when(ldapTemplate.authenticate("", "(" + filterPrefix + "=" + adminUserName + ")", defaultPassWord)).thenAnswer(
                new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        String b = (String) args[1];
                        String c = (String) args[2];
                        if (defaultPassWord.equals(c)) {
                            return true;
                        }
                        return false;
                    }
                });
        this.ldapAuthenticationProvider = new LdapAuthenticationProvider(ldapTemplate, userDetailsService, nacosRoleService, filterPrefix,
                caseSensitive);
        this.ldapAuthenticationProviderForCloseCaseSensitive = new LdapAuthenticationProvider(ldapTemplate, userDetailsService,
                nacosRoleService, filterPrefix, !caseSensitive);
        isAdmin = LdapAuthenticationProvider.class.getDeclaredMethod("isAdmin", String.class);
        isAdmin.setAccessible(true);
        ldapLogin = LdapAuthenticationProvider.class.getDeclaredMethod("ldapLogin", String.class, String.class);
        ldapLogin.setAccessible(true);
    }
    
    @Test
    void testIsAdmin() {
        try {
            Boolean result = (Boolean) isAdmin.invoke(ldapAuthenticationProvider, adminUserName);
            assertTrue(result);
        } catch (IllegalAccessException e) {
            fail();
        } catch (InvocationTargetException e) {
            fail();
        }
        try {
            Boolean result = (Boolean) isAdmin.invoke(ldapAuthenticationProvider, normalUserName);
            assertFalse(result);
        } catch (IllegalAccessException e) {
            fail();
        } catch (InvocationTargetException e) {
            fail();
        }
        
    }
    
    @Test
    void testldapLogin() {
        try {
            Boolean result = (Boolean) ldapLogin.invoke(ldapAuthenticationProvider, adminUserName, defaultPassWord);
            assertTrue(result);
        } catch (IllegalAccessException e) {
            fail();
        } catch (InvocationTargetException e) {
            fail();
        }
        try {
            Boolean result = (Boolean) ldapLogin.invoke(ldapAuthenticationProvider, adminUserName, "123");
            assertFalse(result);
        } catch (IllegalAccessException e) {
            fail();
        } catch (InvocationTargetException e) {
            fail();
        }
    }
    
    @Test
    void testDefaultCaseSensitive() {
        String userName = StringUtils.upperCase(normalUserName);
        when(ldapTemplate.authenticate("", "(" + filterPrefix + "=" + userName + ")", defaultPassWord)).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                String b = (String) args[1];
                String c = (String) args[2];
                if (defaultPassWord.equals(c)) {
                    return true;
                }
                return false;
            }
        });
        User userUpperCase = new User();
        userUpperCase.setUsername(LDAP_PREFIX + userName);
        userUpperCase.setPassword(defaultPassWord);
        when(userDetailsService.loadUserByUsername(LDAP_PREFIX + userName)).thenReturn(new NacosUserDetails(userUpperCase));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userName, defaultPassWord);
        Authentication result = ldapAuthenticationProvider.authenticate(authentication);
        NacosUserDetails nacosUserDetails = (NacosUserDetails) result.getPrincipal();
        assertEquals(nacosUserDetails.getUsername(), LDAP_PREFIX + userName);
    }
    
    @Test
    void testCloseCaseSensitive() {
        when(ldapTemplate.authenticate("", "(" + filterPrefix + "=" + normalUserName + ")", defaultPassWord)).thenAnswer(
                new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        String b = (String) args[1];
                        String c = (String) args[2];
                        if (defaultPassWord.equals(c)) {
                            return true;
                        }
                        return false;
                    }
                });
        User user = new User();
        user.setUsername(LDAP_PREFIX + normalUserName);
        user.setPassword(defaultPassWord);
        when(userDetailsService.loadUserByUsername(LDAP_PREFIX + normalUserName)).thenReturn(new NacosUserDetails(user));
        Authentication authentication = new UsernamePasswordAuthenticationToken(StringUtils.upperCase(normalUserName), defaultPassWord);
        Authentication result = ldapAuthenticationProviderForCloseCaseSensitive.authenticate(authentication);
        NacosUserDetails nacosUserDetails = (NacosUserDetails) result.getPrincipal();
        assertEquals(nacosUserDetails.getUsername(), LDAP_PREFIX + normalUserName);
    }
}
