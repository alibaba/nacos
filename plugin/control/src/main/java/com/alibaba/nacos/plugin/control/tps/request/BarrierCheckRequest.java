package com.alibaba.nacos.plugin.control.tps.request;

public class BarrierCheckRequest {
    
    private String pointName;
    
    private long timestamp = System.currentTimeMillis();
    
    private long count = 1;

    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getCount() {
        return count;
    }
    
    public void setCount(long count) {
        this.count = count;
    }
    
}
