package com.alibaba.nacos.core.remote.control;

public class ClientIpMonitorKey extends MonitorKey {
    
    
    public ClientIpMonitorKey() {
    
    }
    
    public ClientIpMonitorKey(String clientIp) {
        this.key = clientIp;
    }
    
    @Override
    public String getType() {
        return "clientIp";
    }
    
}
