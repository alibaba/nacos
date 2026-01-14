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

import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import com.alibaba.nacos.console.filter.NacosConsoleAuthFilter;
import com.alibaba.nacos.console.filter.XssFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.controller.compatibility.ApiCompatibilityFilter;
import com.alibaba.nacos.core.paramcheck.ParamCheckerFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
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
@Configuration
public class ConsoleWebConfig {
    
    private final ControllerMethodsCache methodsCache;
    
    public ConsoleWebConfig(ControllerMethodsCache methodsCache) {
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
        ConsoleCorsConfig corsConfig = new ConsoleCorsConfig();
        config.setAllowCredentials(corsConfig.isAllowCredentials());
        if (corsConfig.getAllowedHeaders().isEmpty()) {
            config.addAllowedHeader("*");
        } else {
            config.setAllowedHeaders(corsConfig.getAllowedHeaders());
        }
        config.setMaxAge(corsConfig.getMaxAge());
        if (corsConfig.getAllowedMethods().isEmpty()) {
            config.addAllowedMethod("*");
        } else {
            config.setAllowedMethods(corsConfig.getAllowedMethods());
        }
        if (corsConfig.getAllowedOrigins().isEmpty()) {
            config.addAllowedOriginPattern("*");
        } else {
            config.setAllowedOrigins(corsConfig.getAllowedOrigins());
        }
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
        return new NacosConsoleAuthFilter(NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE), methodsCache);
    }
    
    @Bean
    public FilterRegistrationBean<ParamCheckerFilter> consoleParamCheckerFilterRegistration(
            ParamCheckerFilter consoleParamCheckerFilter) {
        FilterRegistrationBean<ParamCheckerFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(consoleParamCheckerFilter);
        registration.addUrlPatterns("/*");
        registration.setName("consoleParamCheckerFilter");
        registration.setOrder(8);
        return registration;
    }
    
    @Bean
    public ParamCheckerFilter consoleParamCheckerFilter(ControllerMethodsCache methodsCache) {
        return new ParamCheckerFilter(methodsCache);
    }
    
    @Bean
    public ApiCompatibilityFilter consoleApiCompatibilityFilter(ControllerMethodsCache methodsCache) {
        return new ApiCompatibilityFilter(methodsCache, null);
    }
    
    @Bean
    public FilterRegistrationBean<ApiCompatibilityFilter> consoleApiCompatibilityFilterRegistration(
            ApiCompatibilityFilter consoleApiCompatibilityFilter) {
        FilterRegistrationBean<ApiCompatibilityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(consoleApiCompatibilityFilter);
        registration.addUrlPatterns("/v1/*", "/v2/*");
        registration.setName("consoleApiCompatibilityFilter");
        registration.setOrder(5);
        return registration;
    }
    
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder.timeZone(ZoneId.systemDefault().toString());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests((authorizeHttpRequests) -> authorizeHttpRequests.requestMatchers("/**").permitAll());
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
    
    @Bean
    public NacosApiExceptionHandler nacosApiExceptionHandler() {
        return new NacosApiExceptionHandler();
    }
}
