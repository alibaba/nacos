package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.RuleBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;

import java.util.concurrent.TimeUnit;

public class FlowedCountRuleBarrierCreator implements RuleBarrierCreator {
    
    private static FlowedCountRuleBarrierCreator INSTANCE = new FlowedCountRuleBarrierCreator();
    
    private FlowedCountRuleBarrierCreator() {
    }
    
    public static final FlowedCountRuleBarrierCreator getInstance() {
        return INSTANCE;
    }
    
    @Override
    public RuleBarrier createRuleBarrier(String name, String pattern, TimeUnit period) {
        return new FlowedCountRuleBarrier(name, pattern, period);
    }
    
    @Override
    public String name() {
        return "flowedcount";
    }
}
