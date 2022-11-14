package com.alibaba.nacos.plugin.control.connection.interceptor;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.tps.RuleBarrier;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

public class InterceptorHolder {
    
    static Collection<ConnectionInterceptor> connectionInterceptors = NacosServiceLoader
            .load(ConnectionInterceptor.class);
    
    static {
        connectionInterceptors.stream()
                .sorted(Comparator.comparing(ConnectionInterceptor::getOrder, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
    
    public static Collection<ConnectionInterceptor> getInterceptors() {
        return connectionInterceptors;
    }
}
