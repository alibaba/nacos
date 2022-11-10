package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRuleBarrier;

import java.util.concurrent.TimeUnit;

public class FlowedLocalSimpleCountRuleBarrier extends FlowedRuleBarrier {
    
    public FlowedLocalSimpleCountRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period,
            String model) {
        super(pointName, ruleName, pattern, period, model);
    }
    
    @Override
    RuleBarrier createRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period, String model) {
        return new LocalSimpleCountRuleBarrier(pointName, ruleName, pattern, period, model);
    }
    
    @Override
    public String getBarrierName() {
        return "flowedlocalsimplecount";
    }
    
    @Override
    public LocalSimpleCountRateCounter createSimpleCounter(String name, TimeUnit period) {
        return new LocalSimpleCountRateCounter(name, period);
    }
}
