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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Nacos Auth http config.
 *
 * @author xiweng.yy
 */
@Configuration
@NacosWebBean
@EnableWebSecurity
public class NacosAuthPluginWebConfig {
    
    private static final String SECURITY_IGNORE_URLS_SPILT_CHAR = ",";
    
    private static final String DEFAULT_ALL_PATH_PATTERN = "/**";
    
    private static final String PROPERTY_IGNORE_URLS = "nacos.security.ignore.urls";
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String ignoreUrls = null;
        String authSystemTYpe = NacosServerAuthConfig.getInstance().getNacosAuthSystemType();
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authSystemTYpe)) {
            ignoreUrls = DEFAULT_ALL_PATH_PATTERN;
        } else if (AuthSystemTypes.LDAP.name().equalsIgnoreCase(authSystemTYpe)) {
            ignoreUrls = DEFAULT_ALL_PATH_PATTERN;
        }
        if (StringUtils.isBlank(authSystemTYpe)) {
            ignoreUrls = EnvUtil.getProperty(PROPERTY_IGNORE_URLS, DEFAULT_ALL_PATH_PATTERN);
        }
        if (StringUtils.isBlank(ignoreUrls)) {
            return http.build();
        }
        return http.authorizeHttpRequests().requestMatchers(ignoreUrls.trim().split(SECURITY_IGNORE_URLS_SPILT_CHAR))
                .permitAll().and().csrf().disable().build();
    }
    
    /**
     * Build required {@link AuthenticationManager} for v1 auth API.
     *
     * @return spring security default authentication manager
     * @throws Exception any exception during build authentication manager.
     * @deprecated will be removed after v1 auth API removed.
     */
    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    @Deprecated()
    public AuthenticationManager authenticationManagerBean() throws Exception {
        AuthenticationConfiguration authenticationConfiguration = ApplicationUtils.getBean(
                AuthenticationConfiguration.class);
        return authenticationConfiguration.getAuthenticationManager();
    }
}
