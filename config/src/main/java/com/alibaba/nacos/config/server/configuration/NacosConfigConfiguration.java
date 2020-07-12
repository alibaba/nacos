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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.config.server.filter.NacosWebFilter;
import com.alibaba.nacos.config.server.filter.CurcuitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos Config {@link Configuration} includes required Spring components.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
@Configuration
public class NacosConfigConfiguration {
    
    @Bean
    public FilterRegistrationBean nacosWebFilterRegistration() {
        FilterRegistrationBean<NacosWebFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(nacosWebFilter());
        registration.addUrlPatterns("/v1/cs/*");
        registration.setName("nacosWebFilter");
        registration.setOrder(1);
        return registration;
    }
    
    @Bean
    public NacosWebFilter nacosWebFilter() {
        return new NacosWebFilter();
    }
    
    @Conditional(ConditionDistributedEmbedStorage.class)
    @Bean
    public FilterRegistrationBean transferToLeaderRegistration() {
        FilterRegistrationBean<CurcuitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(transferToLeader());
        registration.addUrlPatterns("/v1/cs/*");
        registration.setName("curcuitFilter");
        registration.setOrder(6);
        return registration;
    }
    
    @Conditional(ConditionDistributedEmbedStorage.class)
    @Bean
    public CurcuitFilter transferToLeader() {
        return new CurcuitFilter();
    }
    
}
