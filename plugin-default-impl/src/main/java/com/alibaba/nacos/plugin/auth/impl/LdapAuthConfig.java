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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.impl.configuration.ConditionOnLdapAuth;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.HashMap;
import java.util.Map;

/**
 * ldap auth config.
 * @author onewe
 */
@Configuration
@EnableAutoConfiguration(exclude = LdapAutoConfiguration.class)
public class LdapAuthConfig {
    
    @Value(("${" + AuthConstants.NACOS_CORE_AUTH_LDAP_URL + ":ldap://localhost:389}"))
    private String ldapUrl;
    
    @Value(("${" + AuthConstants.NACOS_CORE_AUTH_LDAP_BASEDC + ":dc=example,dc=org}"))
    private String ldapBaseDc;
    
    @Value(("${" + AuthConstants.NACOS_CORE_AUTH_LDAP_TIMEOUT + ":3000}"))
    private String ldapTimeOut;
    
    @Value(("${" + AuthConstants.NACOS_CORE_AUTH_LDAP_USERDN + ":cn=admin,dc=example,dc=org}"))
    private String userDn;
    
    @Value(("${" + AuthConstants.NACOS_CORE_AUTH_LDAP_PASSWORD + ":password}"))
    private String password;
    
    @Bean
    @Conditional(ConditionOnLdapAuth.class)
    public LdapTemplate ldapTemplate() {
        LdapContextSource contextSource = new LdapContextSource();
        final Map<String, Object> config = new HashMap<>(16);
        contextSource.setUrl(ldapUrl);
        contextSource.setBase(ldapBaseDc);
        contextSource.setUserDn(userDn);
        contextSource.setPassword(password);
        config.put("java.naming.ldap.attributes.binary", "objectGUID");
        config.put("com.sun.jndi.ldap.connect.timeout", ldapTimeOut);
        contextSource.setPooled(true);
        contextSource.setBaseEnvironmentProperties(config);
        contextSource.afterPropertiesSet();
        return new LdapTemplate(contextSource);
        
    }
    
    @Bean
    @Conditional(ConditionOnLdapAuth.class)
    public LdapAuthenticationProvider ldapAuthenticationProvider(LdapTemplate ldapTemplate,
            NacosUserDetailsServiceImpl userDetailsService, NacosRoleServiceImpl nacosRoleService) {
        return new LdapAuthenticationProvider(ldapTemplate, userDetailsService, nacosRoleService);
    }
    
}
