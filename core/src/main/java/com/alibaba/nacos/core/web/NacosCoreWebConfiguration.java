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

package com.alibaba.nacos.core.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Nacos core web configuration.
 *
 * @author Huang Xiao
 */
@Configuration
public class NacosCoreWebConfiguration {

    /**
     * form size filter registration.
     *
     * @param formSizeFilter form size filter
     * @return filter registration
     * @see com.alibaba.nacos.core.auth.AuthFilter
     */
    @Bean
    public FilterRegistrationBean<FormSizeFilter> formSizeFilterRegistration(FormSizeFilter formSizeFilter) {
        FilterRegistrationBean<FormSizeFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(formSizeFilter);
        registration.addUrlPatterns("/*");
        registration.setName("formSizeFilter");
        // Note: The priority must be higher than "com.alibaba.nacos.core.auth.AuthFilter", otherwise the verification will not take effect.
        registration.setOrder(5);
        return registration;
    }

    /**
     * form size filter.
     *
     * @param maxFormSize max form size (default 2MB, same as Tomcat's default)
     * @return filter
     */
    @Bean
    public FormSizeFilter formSizeFilter(@Value("${server.tomcat.max-http-form-post-size:2MB}") DataSize maxFormSize) {
        return new FormSizeFilter(maxFormSize.toBytes());
    }
}
