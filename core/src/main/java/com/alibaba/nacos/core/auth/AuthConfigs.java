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
package com.alibaba.nacos.core.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Auth related configurations
 *
 * @author nkorange
 * @since 1.2.0
 */
@Component
@Configuration
public class AuthConfigs {

    /**
     * secret key
     */
    @Value("${nacos.core.auth.default.token.secret.key:SecretKey012345678901234567890123456789012345678901234567890123456789}")
    private String secretKey;

    /**
     * Token validity time(ms)
     */
    @Value("${nacos.core.auth.default.token.expire.seconds:1800}")
    private long tokenValidityInSeconds;

    /**
     * If Nacos builtin access control enabled
     */
    @Value("${nacos.core.auth.system.type:}")
    private String nacosAuthSystemType;

    @Value("${nacos.core.auth.enabled:false}")
    private boolean authEnabled;

    public String getSecretKey() {
        return secretKey;
    }

    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }

    public String getNacosAuthSystemType() {
        return nacosAuthSystemType;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    @Bean
    public FilterRegistrationBean authFilterRegistration() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilter());
        registration.addUrlPatterns("/*");
        registration.setName("authFilter");
        registration.setOrder(6);

        return registration;
    }

    @Bean
    public AuthFilter authFilter() {
        return new AuthFilter();
    }

}
