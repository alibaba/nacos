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
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
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

    @Value(("${nacos.core.auth.ldap.binduser:cn=admin,ou=user,dc=company,dc=com}"))
    private String bindLdapUser;

    @Value(("${nacos.core.auth.ldap.bindpwd:123456}"))
    private String bindLdapPwd;

    @Value(("${nacos.core.auth.ldap.basedn:ou=people,dc=company,dc=com}"))
    private String ldapBaseDN;

    @Value(("${nacos.core.auth.ldap.attrname:userPrincipalName}"))
    private String attrName;

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

        if (!ldapLogin(bindLdapUser, bindLdapPwd, ldapBaseDN, attrName, username, password)) {
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

    private boolean ldapLogin(String bindLdapUser, String bindLdapPwd, String ldapBaseDN, String attrName, String username, String password) throws AuthenticationException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, DEFAULT_SECURITY_AUTH);

        env.put(Context.SECURITY_CREDENTIALS, bindLdapPwd);
        env.put(Context.SECURITY_PRINCIPAL, bindLdapUser);
        env.put(TIMEOUT, time);

        LdapContext ctx = null;

        try {
            ctx = new InitialLdapContext(env, null);
            // ldap search
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String userPrincipalName = username+"@staff.sina.com.cn";
            String searchFileter = attrName + "=" + userPrincipalName;
            NamingEnumeration<?> nameEnu = ctx.search(ldapBaseDN, searchFileter, searchControls);

            if (nameEnu == null) {
                System.out.println("DirContext.search() return null. filter : " + searchFileter);
            } else {
                while (nameEnu.hasMoreElements()) {
                    Object obj = nameEnu.nextElement();
                    if (obj instanceof SearchResult) {
                        SearchResult result = (SearchResult) obj;
                        Attributes attrs = result.getAttributes();
                        System.out.println(attrs);
                        if (attrs == null) {
                            System.out.println("can not find user!");
                            return false;
                        } else {
                            Attribute attr = attrs.get(attrName);
                            System.out.println(attr);
                            if (attr != null) {
                                String distinguishedName = (String) attr.get();
                                Hashtable<String, String> envUser = new Hashtable<String, String>();
                                envUser.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, FACTORY);
                                envUser.put(javax.naming.Context.PROVIDER_URL, ldapUrl);
                                envUser.put(javax.naming.Context.SECURITY_AUTHENTICATION, DEFAULT_SECURITY_AUTH);
                                envUser.put(javax.naming.Context.SECURITY_PRINCIPAL, distinguishedName);
                                envUser.put(javax.naming.Context.SECURITY_CREDENTIALS, password);
                                ctx = new InitialLdapContext(envUser, null);
                                return true;
                            }
                        }
                    }
                }
            }
            return false;


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
