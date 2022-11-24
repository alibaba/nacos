package com.alibaba.nacos.plugin.control.tps.request;

/**
 * tps request.
 */
public class TpsCheckRequest {
    
    private String pointName;
    
    private long timestamp = System.currentTimeMillis();
    
    private String connectionId;
    
    private String clientIp;
    
    private long count = 1;
    
    public TpsCheckRequest() {
    
    }
    
    public TpsCheckRequest(String pointName, String connectionId, String clientIp) {
        this.connectionId = connectionId;
        this.clientIp = clientIp;
        this.pointName = pointName;
    }
    
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
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
}
