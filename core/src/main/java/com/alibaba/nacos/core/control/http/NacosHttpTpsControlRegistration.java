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
