package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

import java.util.List;

public class MseTpsCheckRequest extends TpsCheckRequest {
    
    List<MonitorKey> monitorKeys;
    
    long flow;
    
    public long getFlow() {
        return flow;
    }
    
    public void setFlow(long flow) {
        this.flow = flow;
    }
    
    public MseBarrierCheckRequest buildBarrierCheckRequest(MonitorKey monitorKey) {
        MseBarrierCheckRequest barrierCheckRequest = new MseBarrierCheckRequest();
        barrierCheckRequest.setCount(this.getCount());
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setPointName(super.getPointName());
        barrierCheckRequest.setTimestamp(this.getTimestamp());
        barrierCheckRequest.setFlow(flow);
        return barrierCheckRequest;
        
    }
    
    public List<MonitorKey> getMonitorKeys() {
        return monitorKeys;
    }
    
    public void setMonitorKeys(List<MonitorKey> monitorKeys) {
        this.monitorKeys = monitorKeys;
    }
}
