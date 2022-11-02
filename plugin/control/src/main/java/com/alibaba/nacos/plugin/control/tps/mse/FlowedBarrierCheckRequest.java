package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;

public class FlowedBarrierCheckRequest extends BarrierCheckRequest {
    
    long flow;
    
    public long getFlow() {
        return flow;
    }
    
    public void setFlow(long flow) {
        this.flow = flow;
    }
}
