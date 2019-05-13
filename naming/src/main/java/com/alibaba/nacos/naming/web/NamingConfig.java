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
package com.alibaba.nacos.naming.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author nkorange
 */
@Configuration
public class NamingConfig {

    @Bean
    public FilterRegistrationBean distroFilterRegistration() {
        FilterRegistrationBean<DistroFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(distroFilter());
        registration.addUrlPatterns("/v1/ns/*");
        registration.setName("distroFilter");
        registration.setOrder(6);

        return registration;
    }

    @Bean
    public FilterRegistrationBean trafficReviseFilterRegistration() {
        FilterRegistrationBean<TrafficReviseFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(trafficReviseFilter());
        registration.addUrlPatterns("/v1/ns/*");
        registration.setName("trafficReviseFilter");
        registration.setOrder(1);

        return registration;
    }

    @Bean
    public FilterRegistrationBean authFilterRegistration() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(authFilter());
        registration.addUrlPatterns("/api/*", "/raft/*");
        registration.setName("authFilter");
        registration.setOrder(5);

        return registration;
    }

    @Bean
    public DistroFilter distroFilter() {
        return new DistroFilter();
    }

    @Bean
    public TrafficReviseFilter trafficReviseFilter() {
        return new TrafficReviseFilter();
    }

    @Bean
    public AuthFilter authFilter() {
        return new AuthFilter();
    }

}
