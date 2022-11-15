package com.alibaba.nacos.config.server.remote.control;

import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

public class ConfigTpsInterceptor extends TpsInterceptor {
    
    @Override
    public String getName() {
        return "config_publish_interceptor";
    }
    
    @Override
    public String getPointName() {
        return "ConfigPublish";
    }
    
    @Override
    public InterceptResult preIntercept(TpsCheckRequest tpsCheckRequest) {
        return null;
    }
    
    @Override
    public InterceptResult postIntercept(TpsCheckRequest tpsCheckRequest, TpsCheckResponse tpsCheckResponse) {
        return null;
    }
}
