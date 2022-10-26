package com.alibaba.nacos.plugin.control.connection.interceptor;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

public class InterceptorHolder {
    
    static Collection<ConnectionInterceptor> connectionInterceptors = NacosServiceLoader
            .load(ConnectionInterceptor.class);
    
    public static Collection<ConnectionInterceptor> getInterceptors() {
        return connectionInterceptors;
    }
}
