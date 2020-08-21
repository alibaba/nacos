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

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth filter config.
 *
 * @author mai.jh
 */
@Configuration
public class AuthConfig {
    
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
