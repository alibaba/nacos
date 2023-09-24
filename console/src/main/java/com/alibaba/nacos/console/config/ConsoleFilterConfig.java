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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.filter.ConsoleParamCheckFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Console filter config.
 *
 * @author zhuoguang
 */
@Configuration
public class ConsoleFilterConfig {
    
    @Bean
    public FilterRegistrationBean<ConsoleParamCheckFilter> consoleParamCheckFilterRegistration() {
        FilterRegistrationBean<ConsoleParamCheckFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(consoleParamCheckFilter());
        registration.addUrlPatterns("/v1/console/*");
        registration.addUrlPatterns("/v2/console/*");
        registration.setName("consoleparamcheckfilter");
        registration.setOrder(8);
        return registration;
    }
    
    @Bean
    public ConsoleParamCheckFilter consoleParamCheckFilter() {
        return new ConsoleParamCheckFilter();
    }
}
