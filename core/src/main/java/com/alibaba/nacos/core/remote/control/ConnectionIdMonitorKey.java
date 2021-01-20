package com.alibaba.nacos.core.remote.control;

public class ConnectionIdMonitorKey extends MonitorKey {
    
    String key;
    
    public ConnectionIdMonitorKey() {
    
    }
    public ConnectionIdMonitorKey(String clientIp) {
        this.key = clientIp;
    }
    
    @Override
    public String getType() {
        return "connectionId";
    }
    
}
