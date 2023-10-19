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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

/**
 * LDAP auth provider.
 *
 * @author zjw
 */
@Deprecated
public class LdapAuthenticationProvider implements AuthenticationProvider {
    
    private final NacosUserDetailsServiceImpl userDetailsService;
    
    private final NacosRoleServiceImpl nacosRoleService;
    
    private final LdapTemplate ldapTemplate;
    
    private final String filterPrefix;
    
    private final boolean caseSensitive;
    
    public LdapAuthenticationProvider(LdapTemplate ldapTemplate, NacosUserDetailsServiceImpl userDetailsService,
            NacosRoleServiceImpl nacosRoleService, String filterPrefix, boolean caseSensitive) {
        this.ldapTemplate = ldapTemplate;
        this.nacosRoleService = nacosRoleService;
        this.userDetailsService = userDetailsService;
        this.filterPrefix = filterPrefix;
        this.caseSensitive = caseSensitive;
    }
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = (String) authentication.getPrincipal();
        String password = (String) authentication.getCredentials();
        
        if (isAdmin(username)) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (PasswordEncoderUtil.matches(password, userDetails.getPassword())) {
                return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
            } else {
                return null;
            }
        }
        
        if (!caseSensitive) {
            username = StringUtils.lowerCase(username);
        }
        
        try {
            if (!ldapLogin(username, password)) {
                return null;
            }
        } catch (Exception e) {
            Loggers.AUTH.error("[LDAP-LOGIN] failed", e);
            return null;
        }
        
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username);
        } catch (UsernameNotFoundException exception) {
            String nacosPassword = PasswordEncoderUtil.encode(AuthConstants.LDAP_DEFAULT_PASSWORD);
            userDetailsService.createUser(AuthConstants.LDAP_PREFIX + username, nacosPassword);
            User user = new User();
            user.setUsername(AuthConstants.LDAP_PREFIX + username);
            user.setPassword(nacosPassword);
            userDetails = new NacosUserDetails(user);
        }
        return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
    }
    
    private boolean isAdmin(String username) {
        List<RoleInfo> roleInfos = nacosRoleService.getRoles(username);
        if (CollectionUtils.isEmpty(roleInfos)) {
            return false;
        }
        for (RoleInfo roleinfo : roleInfos) {
            if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleinfo.getRole())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean ldapLogin(String username, String password) throws AuthenticationException {
        return ldapTemplate.authenticate("", "(" + filterPrefix + "=" + username + ")", password);
    }
    
    @Override
    public boolean supports(Class<?> aClass) {
        return aClass.equals(UsernamePasswordAuthenticationToken.class);
    }
    
}
