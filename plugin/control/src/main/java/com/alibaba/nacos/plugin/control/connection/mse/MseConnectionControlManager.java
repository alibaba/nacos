package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;

import java.util.Collection;
import java.util.Set;

public class MseConnectionControlManager extends NacosConnectionControlManager {
    
    @Override
    public String getName() {
        return "mse";
    }
    
    @Override
    public void applyConnectionLimitRule(ConnectionLimitRule connectionLimitRule) {
        super.applyConnectionLimitRule(connectionLimitRule);
        if (connectionLimitRule instanceof MseConnectionLimitRule) {
            Set<String> disabledInterceptors = ((MseConnectionLimitRule) connectionLimitRule).getDisabledInterceptors();
            Collection<ConnectionInterceptor> interceptors = InterceptorHolder.getInterceptors();
            for (ConnectionInterceptor tpsInterceptor : interceptors) {
                if (disabledInterceptors != null && disabledInterceptors.contains(tpsInterceptor.getName())) {
                    tpsInterceptor.setDisabled(true);
                } else {
                    tpsInterceptor.setDisabled(false);
                }
            }
            
        }
    }
}
