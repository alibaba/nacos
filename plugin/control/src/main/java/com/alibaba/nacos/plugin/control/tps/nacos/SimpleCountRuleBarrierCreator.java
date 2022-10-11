package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.RuleBarrierCreator;

import java.util.concurrent.TimeUnit;

public class SimpleCountRuleBarrierCreator implements RuleBarrierCreator {
    
    private static SimpleCountRuleBarrierCreator INSTANCE = new SimpleCountRuleBarrierCreator();
    
    private SimpleCountRuleBarrierCreator() {
    }
    
    public static final SimpleCountRuleBarrierCreator getInstance() {
        return INSTANCE;
    }
    
    @Override
    public RuleBarrier createRuleBarrier(String name, String pattern, TimeUnit period) {
        return new SimpleCountRuleBarrier(name, pattern, period);
    }
    
    @Override
    public String name() {
        return "simplecount";
    }
}
