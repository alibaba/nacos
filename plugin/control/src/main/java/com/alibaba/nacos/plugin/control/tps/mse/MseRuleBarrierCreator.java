package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.RuleBarrierCreator;

import java.util.concurrent.TimeUnit;

public class MseRuleBarrierCreator implements RuleBarrierCreator {
    
    private static final MseRuleBarrierCreator INSTANCE = new MseRuleBarrierCreator();
    
    public MseRuleBarrierCreator() {
    }
    
    public static final MseRuleBarrierCreator getInstance() {
        return INSTANCE;
    }
    
    @Override
    public RuleBarrier createRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        return new MseRuleBarrier(pointName, ruleName, period);
    }
    
    @Override
    public String name() {
        return "mse";
    }
}
