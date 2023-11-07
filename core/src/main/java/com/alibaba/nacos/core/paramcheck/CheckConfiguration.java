package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ParamCheckerFilter registration.
 *
 * @author 985492783@qq.com
 * @date 2023/11/7 17:52
 */
@Configuration
public class CheckConfiguration {
    
    @Bean
    public FilterRegistrationBean<ParamCheckerFilter> checkerFilterRegistration(ParamCheckerFilter checkerFilter) {
        FilterRegistrationBean<ParamCheckerFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(checkerFilter);
        registration.addUrlPatterns("/*");
        registration.setName("checkerFilter");
        registration.setOrder(8);
        return registration;
    }
    
    @Bean
    public ParamCheckerFilter checkerFilter(ControllerMethodsCache methodsCache) {
        return new ParamCheckerFilter(methodsCache);
    }
}
