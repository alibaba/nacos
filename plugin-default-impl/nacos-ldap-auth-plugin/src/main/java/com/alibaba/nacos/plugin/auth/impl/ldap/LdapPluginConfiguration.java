/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.ldap;

import com.alibaba.nacos.plugin.auth.impl.LdapAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.LdapAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnLdapAuth;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;

/**
 * Spring integration for the LDAP auth plugin.
 *
 * @author onewe
 */
@Configuration(proxyBeanMethods = false)
@Conditional(ConditionOnLdapAuth.class)
public class LdapPluginConfiguration {
    
    @Bean
    public static LdapAuthPluginConfigProvider ldapAuthPluginConfigProvider() {
        return getLdapAuthPluginService()::getConfig;
    }
    
    @Bean
    public LdapTemplateProvider ldapTemplateProvider(
        LdapAuthPluginConfigProvider configProvider) {
        return new DefaultLdapTemplateProvider(configProvider);
    }
    
    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(
        LdapTemplateProvider ldapTemplateProvider, NacosUserService userDetailsService,
        NacosRoleService nacosRoleService, LdapAuthPluginConfigProvider configProvider) {
        return new LdapAuthenticationProvider(ldapTemplateProvider, userDetailsService,
            nacosRoleService, configProvider);
    }
    
    @Bean(name = LdapPluginDependencyChecker.LDAP_AUTHENTICATION_MANAGER_BEAN_NAME)
    public IAuthenticationManager ldapAuthenticatoinManager(
        LdapTemplateProvider ldapTemplateProvider, NacosUserService userDetailsService,
        TokenManagerDelegate jwtTokenManager, NacosRoleService roleService,
        LdapAuthPluginConfigProvider configProvider) {
        return new LdapAuthenticationManager(ldapTemplateProvider, userDetailsService,
            jwtTokenManager, roleService, configProvider);
    }
    
    @Bean
    public GlobalAuthenticationConfigurerAdapter authenticationConfigurer(
        LdapAuthenticationProvider ldapAuthenticationProvider) {
        return new GlobalAuthenticationConfigurerAdapter() {
            
            @Override
            public void init(AuthenticationManagerBuilder auth) {
                auth.authenticationProvider(ldapAuthenticationProvider);
            }
        };
    }
    
    static LdapAuthPluginService getLdapAuthPluginService() {
        AuthPluginService plugin = AuthPluginManager.getInstance().getAllPlugins()
            .get(AuthConstants.LDAP_AUTH_PLUGIN_TYPE);
        if (!(plugin instanceof LdapAuthPluginService)) {
            throw new IllegalStateException("Built-in LDAP auth plugin is not available");
        }
        return (LdapAuthPluginService) plugin;
    }
}
