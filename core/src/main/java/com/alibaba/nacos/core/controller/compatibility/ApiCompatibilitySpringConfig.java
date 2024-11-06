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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of Api Compatibility.
 *
 * @author xiweng.yy
 */
@Configuration
public class ApiCompatibilitySpringConfig {
    
    @Bean
    public ApiCompatibilityFilter apiCompatibilityFilter(ControllerMethodsCache methodsCache) {
        return new ApiCompatibilityFilter(methodsCache);
    }
    
    @Bean
    public FilterRegistrationBean<ApiCompatibilityFilter> apiCompatibilityFilterRegistration(
            ApiCompatibilityFilter apiCompatibilityFilter) {
        FilterRegistrationBean<ApiCompatibilityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(apiCompatibilityFilter);
        registration.addUrlPatterns("/v1/*", "/v2/*");
        registration.setName("apiCompatibilityFilter");
        registration.setOrder(5);
        return registration;
    }
}
