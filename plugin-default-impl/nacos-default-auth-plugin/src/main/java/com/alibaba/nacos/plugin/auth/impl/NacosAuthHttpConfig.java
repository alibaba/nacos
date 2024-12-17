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
import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.plugin.auth.impl.filter.JwtAuthenticationTokenFilter;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsUtils;

/**
 * Nacos Auth http config.
 *
 * @author xiweng.yy
 */
@EnableGlobalMethodSecurity(prePostEnabled = true)
@NacosWebBean
public class NacosAuthHttpConfig {
    
    private static final String LOGIN_ENTRY_POINT = "/v1/auth/login";
    
    private static final String TOKEN_BASED_AUTH_ENTRY_POINT = "/v1/auth/**";
    
    private final TokenManagerDelegate tokenProvider;
    
    private final AuthConfigs authConfigs;
    
    public NacosAuthHttpConfig(TokenManagerDelegate tokenProvider, AuthConfigs authConfigs) {
        this.tokenProvider = tokenProvider;
        this.authConfigs = authConfigs;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (StringUtils.isBlank(authConfigs.getNacosAuthSystemType())) {
            http.csrf().disable().cors()// We don't need CSRF for JWT based authentication
                    .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                    .authorizeRequests().requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                    .antMatchers(LOGIN_ENTRY_POINT).permitAll().and().authorizeRequests()
                    .antMatchers(TOKEN_BASED_AUTH_ENTRY_POINT).authenticated().and().exceptionHandling()
                    .authenticationEntryPoint(new JwtAuthenticationEntryPoint());
            // disable cache
            http.headers().cacheControl();
            
            http.addFilterBefore(new JwtAuthenticationTokenFilter(tokenProvider),
                    UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
    
}
