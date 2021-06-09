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
 * Naming spring configuration.
 *
 * @author nkorange
 */
@Configuration
public class NamingConfig {
    
    private static final String UTL_PATTERNS = "/v1/ns/*";
    
    private static final String DISTRO_FILTER = "distroFilter";
    
    private static final String SERVICE_NAME_FILTER = "serviceNameFilter";
    
    private static final String TRAFFIC_REVISE_FILTER = "trafficReviseFilter";
    
    @Bean
    public FilterRegistrationBean distroFilterRegistration() {
        FilterRegistrationBean<DistroFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(distroFilter());
        registration.addUrlPatterns(UTL_PATTERNS);
        registration.setName(DISTRO_FILTER);
        registration.setOrder(6);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean serviceNameFilterRegistration() {
        FilterRegistrationBean<ServiceNameFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(serviceNameFilter());
        registration.addUrlPatterns(UTL_PATTERNS);
        registration.setName(SERVICE_NAME_FILTER);
        registration.setOrder(5);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean trafficReviseFilterRegistration() {
        FilterRegistrationBean<TrafficReviseFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(trafficReviseFilter());
        registration.addUrlPatterns(UTL_PATTERNS);
        registration.setName(TRAFFIC_REVISE_FILTER);
        registration.setOrder(1);
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
    public ServiceNameFilter serviceNameFilter() {
        return new ServiceNameFilter();
    }
    
}
