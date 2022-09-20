/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.ldap.core.LdapTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LdapAuthenticationProviderTest {
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private NacosRoleServiceImpl nacosRoleService;
    
    @Mock
    private LdapTemplate ldapTemplate;
    
    private LdapAuthenticationProvider ldapAuthenticationProvider;
    
    private List<RoleInfo> roleInfos = new ArrayList<>();
    
    private final String adminUserName = "nacos";
    
    private final String normalUserName = "normal";
    
    private final String filterPrefix = "uid";
    
    Method isAdmin;
    
    Method ldapLogin;
    
    private String defaultPassWord = "nacos";
    
    @Before
    public void setUp() throws NoSuchMethodException {
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
        this.ldapAuthenticationProvider = new LdapAuthenticationProvider(ldapTemplate, userDetailsService,
                nacosRoleService, filterPrefix);
        isAdmin = LdapAuthenticationProvider.class.getDeclaredMethod("isAdmin", String.class);
        isAdmin.setAccessible(true);
        ldapLogin = LdapAuthenticationProvider.class.getDeclaredMethod("ldapLogin", String.class, String.class);
        ldapLogin.setAccessible(true);
    }
    
    @Test
    public void testIsAdmin() {
        try {
            Boolean result = (Boolean) isAdmin.invoke(ldapAuthenticationProvider, adminUserName);
            Assert.assertTrue(result);
        } catch (IllegalAccessException e) {
            Assert.fail();
        } catch (InvocationTargetException e) {
            Assert.fail();
        }
        try {
            Boolean result = (Boolean) isAdmin.invoke(ldapAuthenticationProvider, normalUserName);
            Assert.assertTrue(!result);
        } catch (IllegalAccessException e) {
            Assert.fail();
        } catch (InvocationTargetException e) {
            Assert.fail();
        }
        
    }
    
    @Test
    public void testldapLogin() {
        try {
            Boolean result = (Boolean) ldapLogin.invoke(ldapAuthenticationProvider, adminUserName, defaultPassWord);
            Assert.assertTrue(result);
        } catch (IllegalAccessException e) {
            Assert.fail();
        } catch (InvocationTargetException e) {
            Assert.fail();
        }
        try {
            Boolean result = (Boolean) ldapLogin.invoke(ldapAuthenticationProvider, adminUserName, "123");
            Assert.assertTrue(!result);
        } catch (IllegalAccessException e) {
            Assert.fail();
        } catch (InvocationTargetException e) {
            Assert.fail();
        }
    }
}
