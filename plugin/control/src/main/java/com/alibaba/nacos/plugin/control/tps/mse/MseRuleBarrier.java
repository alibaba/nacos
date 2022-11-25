package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRuleBarrier;

import java.util.concurrent.TimeUnit;

public class MseRuleBarrier extends FlowedRuleBarrier {
    
    String pattern;
    
    int order;
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public MseRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        super(pointName, ruleName, period);
        this.pattern = pattern;
    }
    
    @Override
    RuleBarrier createRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        return new LocalSimpleCountRuleBarrier(pointName, ruleName, period);
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
