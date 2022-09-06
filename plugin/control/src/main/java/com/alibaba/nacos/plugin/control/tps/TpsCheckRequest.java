package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

import java.util.List;

/**
 * tps request.
 */
public class TpsCheckRequest {
    
    private String connectionId;
    
    private String clientIp;
    
    List<MonitorKey> monitorKeys;
    
    public String getConnectionId() {
        return connectionId;
    }
    
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public List<MonitorKey> getMonitorKeys() {
        return monitorKeys;
    }
    
    public void setMonitorKeys(List<MonitorKey> monitorKeys) {
        this.monitorKeys = monitorKeys;
    }
}
