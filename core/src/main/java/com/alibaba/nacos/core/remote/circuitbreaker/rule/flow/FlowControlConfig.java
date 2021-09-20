package com.alibaba.nacos.core.remote.circuitbreaker.rule.flow;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;

import java.util.concurrent.TimeUnit;

public class FlowControlConfig extends CircuitBreakerConfig {

    private long maxLoad = -1;

    public FlowControlConfig() {
        this(100, TimeUnit.SECONDS, MODEL_FUZZY, "");
    }

    public FlowControlConfig(long maxCount) {
        this(maxCount, TimeUnit.SECONDS, MODEL_FUZZY, "");
    }

    public FlowControlConfig(String monitorType) {
        this(100, TimeUnit.SECONDS, MODEL_FUZZY, monitorType);
    }

    public FlowControlConfig(long maxCount, TimeUnit period, String model, String monitorType) {
        this.setMonitorType(monitorType);
        this.maxLoad = maxCount;
        this.setPeriod(period);
        this.setModel(model);
    }

    public void setMaxLoad(long maxCount) { this.maxLoad = maxCount; }

    public long getMaxLoad() { return maxLoad; }
}
