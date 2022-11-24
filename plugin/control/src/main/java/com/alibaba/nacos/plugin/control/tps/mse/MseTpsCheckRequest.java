package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

import java.util.List;

public class MseTpsCheckRequest extends TpsCheckRequest {
    
    private String connectionId;
    
    private String clientIp;
    
    List<MonitorKey> monitorKeys;
    
    long flow;
    
    public long getFlow() {
        return flow;
    }
    
    public void setFlow(long flow) {
        this.flow = flow;
    }
    
    public BarrierCheckRequest buildBarrierCheckRequest(MonitorKey monitorKey) {
        FlowedBarrierCheckRequest barrierCheckRequest = new FlowedBarrierCheckRequest();
        barrierCheckRequest.setCount(this.getCount());
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setPointName(super.getPointName());
        barrierCheckRequest.setTimestamp(this.getTimestamp());
        barrierCheckRequest.setFlow(flow);
        return barrierCheckRequest;
        
    }
    
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
