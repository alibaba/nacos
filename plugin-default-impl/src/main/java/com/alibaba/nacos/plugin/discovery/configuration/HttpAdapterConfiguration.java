package com.alibaba.nacos.plugin.discovery.configuration;

import com.alibaba.nacos.plugin.discovery.HttpServiceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpAdapterConfiguration {
    @Bean
    HttpServiceManager httpServiceManager(){
        return new HttpServiceManager();
    }
    
}
