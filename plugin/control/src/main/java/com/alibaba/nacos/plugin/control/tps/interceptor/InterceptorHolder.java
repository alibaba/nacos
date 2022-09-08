package com.alibaba.nacos.plugin.control.tps.interceptor;

import java.util.ArrayList;
import java.util.List;

public class InterceptorHolder {
    
    
    
    public static List<TpsInterceptor> getInterceptors() {
        return new ArrayList<>();
    }
}
