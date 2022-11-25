package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;

public class MseBarrierCheckRequest extends BarrierCheckRequest {
    
    long flow;
    
    MonitorKey monitorKey;
    
    private boolean monitorOnly = false;
    
    public boolean isMonitorOnly() {
        return monitorOnly;
    }
    
    public void setMonitorOnly(boolean monitorOnly) {
        this.monitorOnly = monitorOnly;
    }
    
    public MonitorKey getMonitorKey() {
        return monitorKey;
    }
    
    public void setMonitorKey(MonitorKey monitorKey) {
        this.monitorKey = monitorKey;
    }
    
    public long getFlow() {
        return flow;
    }
    
    public void setFlow(long flow) {
        this.flow = flow;
    }
}
