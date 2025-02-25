/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.plugin.auth.impl.authenticate.DefaultAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceRemoteImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserServiceRemoteImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;

/**
 * Configuration of console auth service.
 * TODO use {@link Import} to dynamic load auth plugin controller like Mybatis.
 *
 * @author xiweng.yy
 */
@EnabledRemoteHandler
@Import(AuthConfigs.class)
@Configuration
public class NacosConsoleAuthServiceConfig {
    
    private final ControllerMethodsCache methodsCache;
    
    public NacosConsoleAuthServiceConfig(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }
    
    @PostConstruct
    public void registerAuthPathToCache() {
        methodsCache.initClassMethod("com.alibaba.nacos.plugin.auth.impl.controller");
    }
    
    @Bean
    public NacosRoleService nacosRoleService(AuthConfigs authConfigs) {
        return new NacosRoleServiceRemoteImpl(authConfigs);
    }
    
    @Bean
    public NacosUserService nacosUserService(AuthConfigs authConfigs) {
        return new NacosUserServiceRemoteImpl(authConfigs);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public IAuthenticationManager defaultAuthenticationManager(NacosUserService userDetailsService,
            TokenManagerDelegate jwtTokenManager, NacosRoleService roleService) {
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
