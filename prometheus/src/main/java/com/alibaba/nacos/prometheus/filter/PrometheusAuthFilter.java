package com.alibaba.nacos.prometheus.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import static com.alibaba.nacos.prometheus.api.ApiConstants.PROMETHEUS_CONTROLLER_PATH;

/**
 * prometheus auth configuration.
 *
 * @author vividfish
 */
@Configuration
public class PrometheusAuthFilter {
    
    @Bean
    public FilterRegistrationBean<BasicAuthenticationFilter> basicAuthenticationFilter(
            AuthenticationManager authenticationManager) {
        FilterRegistrationBean<BasicAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BasicAuthenticationFilter(authenticationManager));
        registration.addUrlPatterns(PROMETHEUS_CONTROLLER_PATH);
        registration.setName("prometheusBasicAuthenticationFilter");
        registration.setOrder(2);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean<AnonymousAuthenticationFilter> anonymousAuthenticationFilter() {
        FilterRegistrationBean<AnonymousAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AnonymousAuthenticationFilter("annony"));
        registration.addUrlPatterns(PROMETHEUS_CONTROLLER_PATH);
        registration.setName("prometheusAnonymousAuthenticationFilter");
        registration.setOrder(3);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean<AuthorizationFilter> authorizationFilter() {
        FilterRegistrationBean<AuthorizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthorizationFilter(new AuthenticatedAuthorizationManager()));
        registration.addUrlPatterns(PROMETHEUS_CONTROLLER_PATH);
        registration.setName("prometheusAuthorizationFilter");
        registration.setOrder(4);
        return registration;
    }
    
    @Bean
    public FilterRegistrationBean<ExceptionTranslationFilter> exceptionTranslationFilter() {
        FilterRegistrationBean<ExceptionTranslationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ExceptionTranslationFilter(new Http403ForbiddenEntryPoint()));
        registration.addUrlPatterns(PROMETHEUS_CONTROLLER_PATH);
        registration.setName("prometheusExceptionTranslationFilter");
        registration.setOrder(1);
        return registration;
    }
}
