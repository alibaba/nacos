package com.alibaba.nacos.plugin.control.connection.rule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConnectionLimitRule {
    
    private Set<String> monitorIpList = new HashSet<>();
    
    private int countLimit = -1;
    
    private int countLimitPerClientIpDefault = -1;
    
    private Map<String, Integer> countLimitPerClientIp = new HashMap<>();
    
    private Map<String, Integer> countLimitPerClientApp = new HashMap<>();
    
    public int getCountLimit() {
        return countLimit;
    }
    
    public void setCountLimit(int countLimit) {
        this.countLimit = countLimit;
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
    
    public Set<String> getMonitorIpList() {
        return monitorIpList;
    }
    
    public void setMonitorIpList(Set<String> monitorIpList) {
        this.monitorIpList = monitorIpList;
    }
}
