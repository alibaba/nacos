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

package com.alibaba.nacos.console.security.nacos;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.auth.RoleInfo;
import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.console.security.nacos.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.console.security.nacos.users.NacosUserDetails;
import com.alibaba.nacos.console.security.nacos.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.console.utils.PasswordEncoderUtil;
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

import static com.alibaba.nacos.console.security.nacos.roles.NacosRoleServiceImpl.GLOBAL_ADMIN_ROLE;

/**
 * LDAP auth provider.
 *
 * @author zjw
 */
@Component
public class LdapAuthenticationProvider implements AuthenticationProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationProvider.class);
    
    private static final String FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    
    private static final String TIMEOUT = "com.sun.jndi.ldap.connect.timeout";
    
    private static final String DEFAULT_PASSWORD = "nacos";
    
    private static final String LDAP_PREFIX = "LDAP_";
    
    private static final String DEFAULT_SECURITY_AUTH = "simple";
    
    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private NacosRoleServiceImpl nacosRoleService;
    
    @Value(("${nacos.core.auth.ldap.url:ldap://localhost:389}"))
    private String ldapUrl;
    
    @Value(("${nacos.core.auth.ldap.timeout:3000}"))
    private String time;
    
    @Value(("${nacos.core.auth.ldap.userdn:cn={0},ou=user,dc=company,dc=com}"))
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
            userDetails = userDetailsService.loadUserByUsername(LDAP_PREFIX + username);
        } catch (UsernameNotFoundException exception) {
            String nacosPassword = PasswordEncoderUtil.encode(DEFAULT_PASSWORD);
            userDetailsService.createUser(LDAP_PREFIX + username, nacosPassword);
            User user = new User();
            user.setUsername(LDAP_PREFIX + username);
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
            if (GLOBAL_ADMIN_ROLE.equals(roleinfo.getRole())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean ldapLogin(String username, String password) throws AuthenticationException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, DEFAULT_SECURITY_AUTH);
        
        env.put(Context.SECURITY_PRINCIPAL, userNamePattern.replace("{0}", username));
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(TIMEOUT, time);
        LdapContext ctx = null;
        try {
            ctx = new InitialLdapContext(env, null);
        } catch (CommunicationException e) {
            throw new RuntimeException("LDAP Service connect timeout");
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
