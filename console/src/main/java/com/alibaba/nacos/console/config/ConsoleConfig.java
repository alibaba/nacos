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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.filter.NacosConsoleAuthFilter;
import com.alibaba.nacos.console.filter.XssFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.annotation.PostConstruct;
import java.time.ZoneId;

/**
 * Console config.
 *
 * @author yshen
 * @author nkorange
 * @since 1.2.0
 */
@Component
public class ConsoleConfig {
    
    private final ControllerMethodsCache methodsCache;
    
    @Value("${nacos.console.ui.enabled:true}")
    private boolean consoleUiEnabled;
    
    @Value("${nacos.deployment.type:merged}")
    private String type;
    
    public ConsoleConfig(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }
    
    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.console.controller");
    }
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.setMaxAge(18000L);
        config.addAllowedMethod("*");
        config.addAllowedOriginPattern("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
    
    @Bean
    public XssFilter xssFilter() {
        return new XssFilter();
    }
    
    @Bean
    public FilterRegistrationBean<NacosConsoleAuthFilter> authFilterRegistration(NacosConsoleAuthFilter authFilter) {
        FilterRegistrationBean<NacosConsoleAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilter);
        registration.addUrlPatterns("/*");
        registration.setName("consoleAuthFilter");
        registration.setOrder(6);
        return registration;
    }
    
    @Bean
    public NacosConsoleAuthFilter consoleAuthFilter(ControllerMethodsCache methodsCache) {
        return new NacosConsoleAuthFilter(NacosConsoleAuthConfig.getInstance(), methodsCache);
    }
    
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder.timeZone(ZoneId.systemDefault().toString());
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests().requestMatchers("/**").permitAll().and().csrf().disable().build();
    }
    
    public boolean isConsoleUiEnabled() {
        return consoleUiEnabled;
    }
    
    public String getType() {
        return type;
    }
}
