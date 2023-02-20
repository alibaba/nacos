/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * LdapAuthenticatoinManager.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/17 13:25
 */
public class LdapAuthenticationManager extends AbstractAuthenticationManager {
    
    private final String filterPrefix;
    
    private final boolean caseSensitive;
    
    private final LdapTemplate ldapTemplate;
    
    public LdapAuthenticationManager(LdapTemplate ldapTemplate, NacosUserDetailsServiceImpl userDetailsService,
            TokenManagerDelegate jwtTokenManager, NacosRoleServiceImpl roleService, String filterPrefix,
            boolean caseSensitive) {
        super(userDetailsService, jwtTokenManager, roleService);
        this.ldapTemplate = ldapTemplate;
        this.filterPrefix = filterPrefix;
        this.caseSensitive = caseSensitive;
    }
    
    @Override
    public NacosUser authenticate(String username, String rawPassword) throws AccessException {
        if (StringUtils.isBlank(username)) {
            throw new AccessException("user not found!");
        }
        
        if (!caseSensitive) {
            username = username.toLowerCase();
        }
        
        try {
            return super.authenticate(username, rawPassword);
        } catch (AccessException | UsernameNotFoundException ignored) {
            if (Loggers.AUTH.isWarnEnabled()) {
                Loggers.AUTH.warn("try login with LDAP, user: {}", username);
            }
        }
        
        UserDetails userDetails;
        try {
            if (!ldapLogin(username, rawPassword)) {
                throw new AccessException("LDAP login failed.");
            }
            userDetails = userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username);
        } catch (UsernameNotFoundException exception) {
            String ldapUsername = AuthConstants.LDAP_PREFIX + username;
            userDetailsService.createUser(ldapUsername, AuthConstants.LDAP_DEFAULT_ENCODED_PASSWORD);
            User user = new User();
            user.setUsername(ldapUsername);
            user.setPassword(AuthConstants.LDAP_DEFAULT_ENCODED_PASSWORD);
            userDetails = new NacosUserDetails(user);
        } catch (Exception e) {
            Loggers.AUTH.error("[LDAP-LOGIN] failed", e);
            throw new AccessException("user not found");
        }
        
        return new NacosUser(userDetails.getUsername(), jwtTokenManager.createToken(userDetails.getUsername()));
    }
    
    private boolean ldapLogin(String username, String password) {
        return ldapTemplate.authenticate("", new EqualsFilter(filterPrefix, username).toString(), password);
    }
}
