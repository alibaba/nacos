/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.core.web.NacosWebBean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for OIDC authentication plugin.
 * Configures security filter chain to permit OIDC endpoints and all other paths.
 *
 * @author WangzJi
 */
@Configuration
@NacosWebBean
@EnableWebSecurity
@ConditionalOnProperty(name = "nacos.core.auth.system.type", havingValue = "oidc")
public class OidcWebSecurityConfig {
    
    /**
     * Configure security filter chain for OIDC mode.
     * All paths are permitted at Spring Security level because Nacos has its own auth filter.
     *
     * @param http HttpSecurity
     * @return SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.requestMatchers("/**").permitAll());
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
