package com.alibaba.nacos.plugin.control.tps.request;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

import java.util.List;

/**
 * tps request.
 */
public class TpsCheckRequest {
    
    private String pointName;
    
    private long timestamp = System.currentTimeMillis();
    
    private String connectionId;
    
    private String clientIp;
    
    private long count = 1;
    
    List<MonitorKey> monitorKeys;
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getConnectionId() {
        return connectionId;
    }
    
    public long getCount() {
        return count;
    }
    
    public void setCount(long count) {
        this.count = count;
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
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
}
