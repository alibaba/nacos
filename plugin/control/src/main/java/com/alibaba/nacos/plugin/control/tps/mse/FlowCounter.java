package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.TpsMetrics;

public class FlowCounter extends TpsMetrics.Counter {
    
    private long passFlow;
    
    private long deniedFlow;
    
    public FlowCounter(long passCount, long deniedCount) {
        super(passCount, deniedCount);
    }
    
    public long getPassFlow() {
        return passFlow;
    }
    
    public void setPassFlow(long passFlow) {
        this.passFlow = passFlow;
    }
    
    public long getDeniedFlow() {
        return deniedFlow;
    }
    
    public void setDeniedFlow(long deniedFlow) {
        this.deniedFlow = deniedFlow;
    }
    
    @Override
    public String getSimpleLog() {
        return String.join("|", getPassCount() + "", getDeniedCount() + "", getPassFlow() + "", getDeniedFlow() + "");
    }
    
    @Override
    public String toString() {
        return "{" + "passCount=" + getPassCount() + ", deniedCount=" + getDeniedCount() + ",passFlow=" + passFlow
                + ", deniedFlow=" + deniedFlow + '}';
    }
}
