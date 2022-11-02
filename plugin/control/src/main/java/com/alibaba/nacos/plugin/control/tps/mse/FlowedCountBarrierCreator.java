package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.RuleBarrierCreator;

import java.util.concurrent.TimeUnit;

public class FlowedCountBarrierCreator implements RuleBarrierCreator {
    
    private static FlowedCountBarrierCreator INSTANCE = new FlowedCountBarrierCreator();
    
    public FlowedCountBarrierCreator() {
    }
    
    public static final FlowedCountBarrierCreator getInstance() {
        return INSTANCE;
    }
    
    @Override
    public RuleBarrier createRuleBarrier(String name, String pattern, TimeUnit period, String model) {
        return new FlowedLocalSimpleCountRuleBarrier(name, pattern, period, model);
    }
    
    @Override
    public String name() {
        return "flowedcount";
    }
}
