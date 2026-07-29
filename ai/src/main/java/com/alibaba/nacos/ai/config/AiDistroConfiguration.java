/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.web.AiDistroFilter;
import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.naming.core.DistroMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Distro routing configuration.
 *
 * @author Nacos
 */
@Configuration
@NacosWebBean
public class AiDistroConfiguration {
    
    private static final String AGENT_CLIENT_URL_PATTERN = "/v3/client/ai/agents/*";
    
    @Bean
    public AiDistroFilter aiDistroFilter(DistroMapper distroMapper) {
        return new AiDistroFilter(distroMapper);
    }
    
    @Bean
    public FilterRegistrationBean<AiDistroFilter> aiDistroFilterRegistration(
        AiDistroFilter filter) {
        FilterRegistrationBean<AiDistroFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns(AGENT_CLIENT_URL_PATTERN);
        registration.setName("aiDistroFilter");
        registration.setOrder(7);
        return registration;
    }
}
