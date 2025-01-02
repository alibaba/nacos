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

package com.alibaba.nacos.plugin.auth.impl.configuration;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.plugin.auth.impl.authenticate.DefaultAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.annotation.PostConstruct;

/**
 * Spring security config.
 *
 * @author Nacos
 */
@Configuration
public class NacosAuthPluginConfig {
    
    private final NacosUserDetailsServiceImpl userDetailsService;
    
    private final ControllerMethodsCache methodsCache;
    
    public NacosAuthPluginConfig(NacosUserDetailsServiceImpl userDetailsService, ControllerMethodsCache methodsCache) {
        this.userDetailsService = userDetailsService;
        this.methodsCache = methodsCache;
        
    }
    
    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.plugin.auth.impl.controller");
    }
    
    @Bean
    @ConditionalOnMissingBean
    public GlobalAuthenticationConfigurerAdapter authenticationConfigurer() {
        return new GlobalAuthenticationConfigurerAdapter() {
            @Override
            public void init(AuthenticationManagerBuilder auth) throws Exception {
                if (AuthSystemTypes.NACOS.name()
                        .equalsIgnoreCase(NacosServerAuthConfig.getInstance().getNacosAuthSystemType())) {
                    auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
                }
            }
        };
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public IAuthenticationManager defaultAuthenticationManager(NacosUserDetailsServiceImpl userDetailsService,
            TokenManagerDelegate jwtTokenManager, NacosRoleServiceImpl roleService) {
        return new DefaultAuthenticationManager(userDetailsService, jwtTokenManager, roleService);
    }
    
    @Bean
    @ConditionalOnProperty(value = TokenManagerDelegate.NACOS_AUTH_TOKEN_CACHING_ENABLED, havingValue = "false", matchIfMissing = true)
    public TokenManager tokenManager(AuthConfigs authConfigs) {
        return new JwtTokenManager(authConfigs);
    }
    
    @Bean
    @ConditionalOnProperty(value = TokenManagerDelegate.NACOS_AUTH_TOKEN_CACHING_ENABLED, havingValue = "true")
    public TokenManager cachedTokenManager(AuthConfigs authConfigs) {
        return new CachedJwtTokenManager(new JwtTokenManager(authConfigs));
    }
    
    @Bean
    public TokenManagerDelegate tokenManagerDelegate(TokenManager tokenManager) {
        return new TokenManagerDelegate(tokenManager);
    }
}
