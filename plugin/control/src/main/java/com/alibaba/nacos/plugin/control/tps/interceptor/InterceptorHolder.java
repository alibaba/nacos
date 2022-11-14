package com.alibaba.nacos.plugin.control.tps.interceptor;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

public class InterceptorHolder {
    
    static final Collection<TpsInterceptor> INTERCEPTORS = NacosServiceLoader.load(TpsInterceptor.class);
    
    static {
        INTERCEPTORS.stream().sorted(Comparator.comparing(TpsInterceptor::getOrder, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
    
    public static Collection<TpsInterceptor> getInterceptors() {
        return INTERCEPTORS;
    }
}
