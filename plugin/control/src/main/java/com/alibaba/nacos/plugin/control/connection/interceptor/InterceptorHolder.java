package com.alibaba.nacos.plugin.control.connection.interceptor;

import com.alibaba.nacos.plugin.control.tps.interceptor.TpsInterceptor;

import java.util.ArrayList;
import java.util.List;

public class InterceptorHolder {
    
    public static List<ConnectionInterceptor> getInterceptors() {
        return new ArrayList<>();
    }
}
