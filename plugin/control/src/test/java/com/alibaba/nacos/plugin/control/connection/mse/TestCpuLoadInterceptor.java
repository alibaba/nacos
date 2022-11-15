package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.plugin.control.connection.mse.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.mse.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;

public class TestCpuLoadInterceptor extends ConnectionInterceptor {
    
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
