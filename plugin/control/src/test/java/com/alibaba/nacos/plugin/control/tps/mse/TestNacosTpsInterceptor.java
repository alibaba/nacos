package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

import java.util.HashSet;
import java.util.Set;

public class TestNacosTpsInterceptor extends TpsInterceptor {
    
    Set<String> whiteList = new HashSet<>();
    
    public TestNacosTpsInterceptor() {
        whiteList.add("127.0.0.10");
    }
    
    @Override
    public String getName() {
        return "testnacosinter";
    }
    
    @Override
    public String getPointName() {
        return "interceptortest";
    }
    
    @Override
    public InterceptResult preIntercept(MseTpsCheckRequest tpsCheckRequest) {
        return null;
    }
    
    @Override
    public InterceptResult postIntercept(MseTpsCheckRequest tpsCheckRequest, TpsCheckResponse tpsCheckResponse) {
        String clientIp = tpsCheckRequest.getClientIp();
        
        if (!tpsCheckResponse.isSuccess() && whiteList.contains(clientIp)) {
            return InterceptResult.CHECK_PASS;
        }
        return InterceptResult.CHECK_SKIP;
    }
}
