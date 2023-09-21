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

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos http tps control cut point filter registration.
 *
 * @author xiweng.yy
 */
@Configuration
public class NacosHttpTpsControlRegistration {
    
    @Bean
    public FilterRegistrationBean<NacosHttpTpsFilter> tpsFilterRegistration(NacosHttpTpsFilter tpsFilter) {
        FilterRegistrationBean<NacosHttpTpsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tpsFilter);
        //nacos naming
        registration.addUrlPatterns("/v1/ns/*", "/v2/ns/*");
        //nacos config
        registration.addUrlPatterns("/v1/cs/*", "/v2/cs/*");
        registration.setName("tpsFilter");
        registration.setOrder(6);
        return registration;
    }
    
    @Bean
    public NacosHttpTpsFilter tpsFilter(ControllerMethodsCache methodsCache) {
        return new NacosHttpTpsFilter(methodsCache);
    }
}
