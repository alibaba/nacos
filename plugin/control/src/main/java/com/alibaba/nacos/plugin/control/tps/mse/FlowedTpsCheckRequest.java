package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

public class FlowedTpsCheckRequest extends TpsCheckRequest {
    
    long flow;
    
    public long getFlow() {
        return flow;
    }
    
    public void setFlow(long flow) {
        this.flow = flow;
    }
    
    @Override
    public BarrierCheckRequest buildBarrierCheckRequest(MonitorKey monitorKey) {
        FlowedBarrierCheckRequest barrierCheckRequest = new FlowedBarrierCheckRequest();
        barrierCheckRequest.setCount(this.getCount());
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setPointName(super.getPointName());
        barrierCheckRequest.setTimestamp(this.getTimestamp());
        barrierCheckRequest.setFlow(flow);
        return barrierCheckRequest;
        
    }
}
