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
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LdapAuthenticationManagerTest {
    
    @Mock
    private NacosUserService userDetailsService;
    
    @Mock
    private TokenManagerDelegate jwtTokenManager;
    
    @Mock
    private NacosRoleService roleService;
    
    @Mock
    private LdapTemplate ldapTemplate;
    
    private LdapAuthenticationManager ldapAuthenticationManager;
    
    private User user;
    
    @BeforeEach
    void setUp() throws Exception {
        user = new User();
        user.setUsername("nacos");
        user.setPassword(PasswordEncoderUtil.encode("test"));
        ldapAuthenticationManager =
            new LdapAuthenticationManager(ldapTemplate, userDetailsService, jwtTokenManager,
                roleService, "uid", true);
    }
    
    @Test
    void testLdapAuthenticate() throws AccessException {
        NacosUserDetails nacosUserDetails = new NacosUserDetails(user);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(nacosUserDetails);
        NacosUser authenticate = ldapAuthenticationManager.authenticate("nacos", "test");
        assertEquals(user.getUsername(), authenticate.getUserName());
    }
    
    @Test
    void testAuthenticateWithLdapPrefixRejected() {
        assertThrows(AccessException.class,
            () -> ldapAuthenticationManager.authenticate("LDAP_admin", "nacos"));
    }
    
    @Test
    void testAuthenticateWithLdapPrefixLowercaseRejected() {
        assertThrows(AccessException.class,
            () -> ldapAuthenticationManager.authenticate("ldap_admin", "nacos"));
    }
    
    @Test
    void testAuthenticateWithLdapPrefixMixedCaseRejected() {
        assertThrows(AccessException.class,
            () -> ldapAuthenticationManager.authenticate("Ldap_admin", "nacos"));
    }
    
    @Test
    void testAuthenticateWithLdapPrefixCaseInsensitiveRejected() {
        LdapAuthenticationManager caseInsensitiveManager =
            new LdapAuthenticationManager(ldapTemplate,
                userDetailsService, jwtTokenManager, roleService, "", false);
        assertThrows(AccessException.class,
            () -> caseInsensitiveManager.authenticate("LDAP_admin", "nacos"));
    }
    
    @Test
    void testAuthenticateWithBlankUsername() {
        assertThrows(AccessException.class,
            () -> ldapAuthenticationManager.authenticate(" ", "test"));
    }
    
    @Test
    void testAuthenticateWithExistingLdapUser() throws AccessException {
        String username = "ldap";
        String password = "ldapPassword";
        User ldapUser = createUser(AuthConstants.LDAP_PREFIX + username,
            AuthConstants.LDAP_DEFAULT_ENCODED_PASSWORD);
        when(userDetailsService.loadUserByUsername(username))
            .thenThrow(new UsernameNotFoundException("missing"));
        when(ldapTemplate.authenticate("", "(uid=" + username + ")", password)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username))
            .thenReturn(new NacosUserDetails(ldapUser));
        when(jwtTokenManager.createToken(AuthConstants.LDAP_PREFIX + username)).thenReturn("token");
        
        NacosUser actual = ldapAuthenticationManager.authenticate(username, password);
        
        assertEquals(AuthConstants.LDAP_PREFIX + username, actual.getUserName());
        assertEquals("token", actual.getToken());
    }
    
    @Test
    void testAuthenticateCreatesMissingLdapUser() throws AccessException {
        String username = "newUser";
        String password = "ldapPassword";
        when(userDetailsService.loadUserByUsername(username))
            .thenThrow(new UsernameNotFoundException("missing"));
        when(ldapTemplate.authenticate("", "(uid=" + username + ")", password)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username))
            .thenThrow(new UsernameNotFoundException("missing"));
        when(jwtTokenManager.createToken(AuthConstants.LDAP_PREFIX + username)).thenReturn("token");
        
        NacosUser actual = ldapAuthenticationManager.authenticate(username, password);
        
        assertEquals(AuthConstants.LDAP_PREFIX + username, actual.getUserName());
        assertEquals("token", actual.getToken());
        verify(userDetailsService).createUser(AuthConstants.LDAP_PREFIX + username,
            AuthConstants.LDAP_DEFAULT_ENCODED_PASSWORD, false);
    }
    
    @Test
    void testAuthenticateWithFailedLdapLogin() {
        String username = "ldap";
        String password = "ldapPassword";
        when(userDetailsService.loadUserByUsername(username))
            .thenThrow(new UsernameNotFoundException("missing"));
        when(ldapTemplate.authenticate("", "(uid=" + username + ")", password)).thenReturn(false);
        
        assertThrows(AccessException.class,
            () -> ldapAuthenticationManager.authenticate(username, password));
    }
    
    private User createUser(String username, String password) {
        User result = new User();
        result.setUsername(username);
        result.setPassword(password);
        return result;
    }
}
