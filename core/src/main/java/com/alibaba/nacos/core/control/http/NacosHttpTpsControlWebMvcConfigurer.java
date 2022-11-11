package com.alibaba.nacos.core.control.http;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class NacosHttpTpsControlWebMvcConfigurer implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new NacosHttpTpsControlInterceptor());
    }
}
