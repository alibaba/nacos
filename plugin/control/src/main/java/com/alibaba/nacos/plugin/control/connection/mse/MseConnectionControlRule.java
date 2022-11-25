package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MseConnectionControlRule extends ConnectionControlRule {
    
    Set<String> disabledInterceptors;
    
    String monitorType = MonitorType.MONITOR.getType();
    
    private int countLimitPerClientIpDefault = -1;
    
    private Map<String, Integer> countLimitPerClientIp = new HashMap<>();
    
    private Map<String, Integer> countLimitPerClientApp = new HashMap<>();
    
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
    
    public int getCountLimitPerClientIpDefault() {
        return countLimitPerClientIpDefault;
    }
    
    public void setCountLimitPerClientIpDefault(int countLimitPerClientIpDefault) {
        this.countLimitPerClientIpDefault = countLimitPerClientIpDefault;
    }
    
    public int getCountLimitOfIp(String clientIp) {
        if (countLimitPerClientIp.containsKey(clientIp)) {
            Integer integer = countLimitPerClientIp.get(clientIp);
            if (integer != null && integer >= 0) {
                return integer;
            }
        }
        return -1;
    }
    
    public int getCountLimitOfApp(String appName) {
        if (countLimitPerClientApp.containsKey(appName)) {
            Integer integer = countLimitPerClientApp.get(appName);
            if (integer != null && integer >= 0) {
                return integer;
            }
        }
        return -1;
    }
    
    public Map<String, Integer> getCountLimitPerClientIp() {
        return countLimitPerClientIp;
    }
    
    public void setCountLimitPerClientIp(Map<String, Integer> countLimitPerClientIp) {
        this.countLimitPerClientIp = countLimitPerClientIp;
    }
    
    public Map<String, Integer> getCountLimitPerClientApp() {
        return countLimitPerClientApp;
    }
    
    public void setCountLimitPerClientApp(Map<String, Integer> countLimitPerClientApp) {
        this.countLimitPerClientApp = countLimitPerClientApp;
    }
    
}
