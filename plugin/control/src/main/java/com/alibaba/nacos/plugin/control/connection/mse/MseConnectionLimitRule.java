package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;

import java.util.Set;

public class MseConnectionLimitRule extends ConnectionLimitRule {
    
    Set<String> disabledInterceptors;
    
    public Set<String> getDisabledInterceptors() {
        return disabledInterceptors;
    }
    
    public void setDisabledInterceptors(Set<String> disabledInterceptors) {
        this.disabledInterceptors = disabledInterceptors;
    }
}
