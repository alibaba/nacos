package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;

public class TestCpuLoadInterceptor implements ConnectionInterceptor {
    
    @Override
    public String getName() {
        return "cputestinterceptor";
    }
    
    @Override
    public InterceptResult preIntercept(ConnectionCheckRequest connectionCheckRequest) {
        
        if (CpuTestUtils.cpuOverLoad) {
            return InterceptResult.CHECK_DENY;
        } else {
            return InterceptResult.CHECK_SKIP;
        }
    }
    
    @Override
    public InterceptResult postIntercept(ConnectionCheckRequest connectionCheckRequest,
            ConnectionCheckResponse connectionCheckResponse) {
        return InterceptResult.CHECK_SKIP;
    }
}
