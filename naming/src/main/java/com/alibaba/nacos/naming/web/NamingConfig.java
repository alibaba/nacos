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
    
    private static final String URL_PATTERNS = "/v1/ns/*";
    
    private static final String URL_PATTERNS_V2 = "/v2/ns/*";
    
    private static final String DISTRO_FILTER = "distroFilter";
    
    private static final String SERVICE_NAME_FILTER = "serviceNameFilter";
    
    private static final String TRAFFIC_REVISE_FILTER = "trafficReviseFilter";
    
    private static final String CLIENT_ATTRIBUTES_FILTER = "clientAttributes_filter";
    
    private static final String NAMING_PARAM_CHECK_FILTER = "namingparamCheckFilter";
    
    @Bean
    public FilterRegistrationBean<DistroFilter> distroFilterRegistration() {
        FilterRegistrationBean<DistroFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(distroFilter());
        registration.addUrlPatterns(URL_PATTERNS);
        registration.setName(DISTRO_FILTER);
        registration.setOrder(7);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean<ServiceNameFilter> serviceNameFilterRegistration() {
        FilterRegistrationBean<ServiceNameFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(serviceNameFilter());
        registration.addUrlPatterns(URL_PATTERNS);
        registration.setName(SERVICE_NAME_FILTER);
        registration.setOrder(5);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean<TrafficReviseFilter> trafficReviseFilterRegistration() {
        FilterRegistrationBean<TrafficReviseFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(trafficReviseFilter());
        registration.addUrlPatterns(URL_PATTERNS);
        registration.setName(TRAFFIC_REVISE_FILTER);
        registration.setOrder(1);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean<ClientAttributesFilter> clientAttributesFilterRegistration() {
        FilterRegistrationBean<ClientAttributesFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(clientAttributesFilter());
        registration.addUrlPatterns(URL_PATTERNS, URL_PATTERNS_V2);
        registration.setName(CLIENT_ATTRIBUTES_FILTER);
        registration.setOrder(8);
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
    
    @Bean
    public ClientAttributesFilter clientAttributesFilter() {
        return new ClientAttributesFilter();
    }
}
