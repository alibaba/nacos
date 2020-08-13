package com.alibaba.nacos.core.auth;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth filter config
 * @author mai.jh
 */
@Configuration
public class AuthFilterConfig {
    
    @Bean
    public FilterRegistrationBean authFilterRegistration() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilter());
        registration.addUrlPatterns("/*");
        registration.setName("authFilter");
        registration.setOrder(6);
        
        return registration;
    }
    
    @Bean
    public AuthFilter authFilter() {
        return new AuthFilter();
    }
}
