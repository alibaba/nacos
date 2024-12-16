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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.plugin.auth.impl.authenticate.AuthenticationManagerDelegator;
import com.alibaba.nacos.plugin.auth.impl.authenticate.DefaultAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.LdapAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.annotation.PostConstruct;

/**
 * Spring security config.
 *
 * @author Nacos
 */
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class NacosAuthConfig {
    
    private static final String SECURITY_IGNORE_URLS_SPILT_CHAR = ",";
    
    private static final String DEFAULT_ALL_PATH_PATTERN = "/**";
    
    private static final String PROPERTY_IGNORE_URLS = "nacos.security.ignore.urls";
    
    private final Environment env;
    
    private final AuthConfigs authConfigs;
    
    private final NacosUserDetailsServiceImpl userDetailsService;
    
    private final LdapAuthenticationProvider ldapAuthenticationProvider;
    
    private final ControllerMethodsCache methodsCache;
    
    public NacosAuthConfig(Environment env, AuthConfigs authConfigs, NacosUserDetailsServiceImpl userDetailsService,
            ObjectProvider<LdapAuthenticationProvider> ldapAuthenticationProvider,
            ControllerMethodsCache methodsCache) {
        
        this.env = env;
        this.authConfigs = authConfigs;
        this.userDetailsService = userDetailsService;
        this.ldapAuthenticationProvider = ldapAuthenticationProvider.getIfAvailable();
        this.methodsCache = methodsCache;
        
    }
    
    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.plugin.auth.impl.controller");
    }
    
    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    public AuthenticationManager authenticationManagerBean() throws Exception {
        AuthenticationConfiguration authenticationConfiguration = ApplicationUtils.getBean(
                AuthenticationConfiguration.class);
        return authenticationConfiguration.getAuthenticationManager();
    }
    
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> {
            String ignoreUrls = null;
            if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
                ignoreUrls = DEFAULT_ALL_PATH_PATTERN;
            } else if (AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
                ignoreUrls = DEFAULT_ALL_PATH_PATTERN;
            }
            if (StringUtils.isBlank(authConfigs.getNacosAuthSystemType())) {
                ignoreUrls = env.getProperty(PROPERTY_IGNORE_URLS, DEFAULT_ALL_PATH_PATTERN);
            }
            if (StringUtils.isNotBlank(ignoreUrls)) {
                for (String each : ignoreUrls.trim().split(SECURITY_IGNORE_URLS_SPILT_CHAR)) {
                    web.ignoring().antMatchers(each.trim());
                }
            }
        };
    }
    
    @Bean
    public GlobalAuthenticationConfigurerAdapter authenticationConfigurer() {
        return new GlobalAuthenticationConfigurerAdapter() {
            @Override
            public void init(AuthenticationManagerBuilder auth) throws Exception {
                if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
                    auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
                } else if (AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
                    auth.authenticationProvider(ldapAuthenticationProvider);
                }
            }
        };
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    @Primary
    public IAuthenticationManager authenticationManager(
            ObjectProvider<LdapAuthenticationManager> ldapAuthenticatoinManagerObjectProvider,
            ObjectProvider<DefaultAuthenticationManager> defaultAuthenticationManagers, AuthConfigs authConfigs) {
        return new AuthenticationManagerDelegator(defaultAuthenticationManagers,
                ldapAuthenticatoinManagerObjectProvider, authConfigs);
    }
    
    @Bean
    public IAuthenticationManager defaultAuthenticationManager(NacosUserDetailsServiceImpl userDetailsService,
            TokenManagerDelegate jwtTokenManager, NacosRoleServiceImpl roleService) {
        return new DefaultAuthenticationManager(userDetailsService, jwtTokenManager, roleService);
    }
}
