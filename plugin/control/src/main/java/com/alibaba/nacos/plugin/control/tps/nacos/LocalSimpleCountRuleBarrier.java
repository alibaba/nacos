package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;

import java.util.concurrent.TimeUnit;

public class LocalSimpleCountRuleBarrier extends SimpleCountRuleBarrier {
    
    
    public LocalSimpleCountRuleBarrier(String name, String pattern, TimeUnit period) {
        super(name, pattern, period, RuleModel.FUZZY.name());
    }
    
    public LocalSimpleCountRuleBarrier(String name, String pattern, TimeUnit period, String model) {
        super(name, pattern, period, model);
    }
    
    public LocalSimpleCountRateCounter createSimpleCounter(String name, TimeUnit period) {
        return new LocalSimpleCountRateCounter(name, period);
    }
    
}
