package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;

import java.util.Set;

public class MseConnectionLimitRule extends ConnectionLimitRule {
    
    Set<String> disabledInterceptors;
    
    String monitorType = MonitorType.MONITOR.getType();
    
    public Set<String> getDisabledInterceptors() {
        return disabledInterceptors;
    }
    
    public void setDisabledInterceptors(Set<String> disabledInterceptors) {
        this.disabledInterceptors = disabledInterceptors;
    }
    
    public boolean isInterceptMode() {
        return MonitorType.INTERCEPT.type.equalsIgnoreCase(monitorType);
    }
    
    public String getMonitorType() {
        return monitorType;
    }
    
    public void setMonitorType(String monitorType) {
        this.monitorType = monitorType;
    }
}
