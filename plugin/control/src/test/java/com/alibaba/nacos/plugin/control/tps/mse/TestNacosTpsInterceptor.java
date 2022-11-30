package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

import java.util.HashSet;
import java.util.Set;

public class TestNacosTpsInterceptor extends TpsInterceptor {
    
    Set<String> postwhiteList = new HashSet<>();
    
    Set<String> prewhiteList = new HashSet<>();
    
    Set<String> postBlackList = new HashSet<>();
    
    Set<String> preBlackList = new HashSet<>();
    
    public TestNacosTpsInterceptor() {
        postwhiteList.add("127.0.0.1_post");
        prewhiteList.add("127.0.0.1_pre");
        postBlackList.add("127.0.0.1_post_black");
        preBlackList.add("127.0.0.1_pre_black");
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
        String clientIp = tpsCheckRequest.getClientIp();
        if (clientIp != null && prewhiteList.contains(clientIp)) {
            return InterceptResult.CHECK_PASS;
        }
        if (clientIp != null && preBlackList.contains(clientIp)) {
            return InterceptResult.CHECK_DENY;
        }
        
        return null;
    }
    
    @Override
    public InterceptResult postIntercept(MseTpsCheckRequest tpsCheckRequest, TpsCheckResponse tpsCheckResponse) {
        String clientIp = tpsCheckRequest.getClientIp();
        
        if (!tpsCheckResponse.isSuccess() && postwhiteList.contains(clientIp)) {
            return InterceptResult.CHECK_PASS;
        }
        
        if (tpsCheckResponse.isSuccess() && postBlackList.contains(clientIp)) {
            return InterceptResult.CHECK_DENY;
        }
        return InterceptResult.CHECK_SKIP;
    }
}
