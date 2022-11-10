package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.RuleBarrierCreator;

import java.util.concurrent.TimeUnit;

public class LocalSimpleCountBarrierCreator implements RuleBarrierCreator {
    
    private static final LocalSimpleCountBarrierCreator INSTANCE = new LocalSimpleCountBarrierCreator();
    
    public LocalSimpleCountBarrierCreator() {
    }
    
    public static final LocalSimpleCountBarrierCreator getInstance() {
        return INSTANCE;
    }
    
    @Override
    public RuleBarrier createRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period,
            String model) {
        return new LocalSimpleCountRuleBarrier(pointName, ruleName, pattern, period, model);
    }
    
    @Override
    public String name() {
        return "localsimplecount";
    }
}
