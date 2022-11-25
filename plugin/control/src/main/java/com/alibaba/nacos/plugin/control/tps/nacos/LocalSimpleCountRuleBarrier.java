package com.alibaba.nacos.plugin.control.tps.nacos;

import java.util.concurrent.TimeUnit;

public class LocalSimpleCountRuleBarrier extends SimpleCountRuleBarrier {
    
    public LocalSimpleCountRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        super(pointName, ruleName, period);
    }
    
    public RateCounter createSimpleCounter(String name, TimeUnit period) {
        return new LocalSimpleCountRateCounter(name, period);
    }
    
    @Override
    public String getBarrierName() {
        return "localsimplecount";
    }
}
