package com.alibaba.nacos.plugin.control.tps.interceptor;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

public class InterceptorHolder {
    
    static final Collection<TpsInterceptor> INTERCEPTORS = NacosServiceLoader.load(TpsInterceptor.class);
    
    public static Collection<TpsInterceptor> getInterceptors() {
        return INTERCEPTORS;
    }
}
