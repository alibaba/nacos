/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.constant.Constants;
import com.alibaba.nacos.auth.roles.NacosAuthRoleServiceImpl;
import com.alibaba.nacos.auth.roles.RoleInfo;
import com.alibaba.nacos.auth.users.NacosAuthUserDetailsServiceImpl;
import com.alibaba.nacos.auth.users.NacosUserDetails;
import com.alibaba.nacos.auth.users.User;
import com.alibaba.nacos.auth.util.PasswordEncoderUtil;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;
import java.util.List;

/**
 * LDAP auth provider.
 *
 * @author zjw
 */
@Component
public class LdapAuthenticationProvider implements AuthenticationProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationProvider.class);
    
    @Autowired
    private NacosAuthUserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private NacosAuthRoleServiceImpl nacosRoleService;
    
    @Value("${" + Constants.Ldap.NACOS_CORE_AUTH_LDAP_URL + ":ldap://localhost:389}")
    private String ldapUrl;
    
    @Value("${" + Constants.Ldap.NACOS_CORE_AUTH_LDAP_TIMEOUT + ":3000}")
    private String time;
    
    @Value("${" + Constants.Ldap.NACOS_CORE_AUTH_LDAP_USERDN + ":cn={0},ou=user,dc=company,dc=com}")
    private String userNamePattern;
    
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
        
        if (!ldapLogin(username, password)) {
            return null;
        }
        
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(Constants.Ldap.LDAP_PREFIX + username);
        } catch (UsernameNotFoundException exception) {
            String nacosPassword = PasswordEncoderUtil.encode(Constants.Ldap.DEFAULT_PASSWORD);
            userDetailsService.createUser(Constants.Ldap.LDAP_PREFIX + username, nacosPassword);
            User user = new User();
            user.setUsername(Constants.Ldap.LDAP_PREFIX + username);
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
            if (Constants.Auth.GLOBAL_ADMIN_ROLE.equals(roleinfo.getRole())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean ldapLogin(String username, String password) throws AuthenticationException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, Constants.Ldap.FACTORY);
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, Constants.Ldap.DEFAULT_SECURITY_AUTH);
        
        env.put(Context.SECURITY_PRINCIPAL, userNamePattern.replace("{0}", username));
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Constants.Ldap.TIMEOUT, time);
        LdapContext ctx = null;
        try {
            ctx = new InitialLdapContext(env, null);
        } catch (CommunicationException e) {
            LOG.error("LDAP Service connect timeout:{}", e.getMessage());
            throw new RuntimeException("LDAP Service connect timeout");
        } catch (javax.naming.AuthenticationException e) {
            LOG.error("login error:{}", e.getMessage());
            throw new RuntimeException("login error!");
        } catch (Exception e) {
            LOG.warn("Exception cause by:{}", e.getMessage());
            return false;
        } finally {
            closeContext(ctx);
        }
        return true;
    }
    
    @Override
    public boolean supports(Class<?> aClass) {
        return aClass.equals(UsernamePasswordAuthenticationToken.class);
    }
    
    private void closeContext(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception e) {
                LOG.error("Exception closing context", e);
            }
        }
    }
}
