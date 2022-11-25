package com.alibaba.nacos.plugin.control.connection.rule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConnectionControlRule {
    
    private Set<String> monitorIpList = new HashSet<>();
    
    private int countLimit = -1;
    
    public int getCountLimit() {
        return countLimit;
    }
    
    public void setCountLimit(int countLimit) {
        this.countLimit = countLimit;
    }
    
    public Set<String> getMonitorIpList() {
        return monitorIpList;
    }
    
    public void setMonitorIpList(Set<String> monitorIpList) {
        this.monitorIpList = monitorIpList;
    }
}
