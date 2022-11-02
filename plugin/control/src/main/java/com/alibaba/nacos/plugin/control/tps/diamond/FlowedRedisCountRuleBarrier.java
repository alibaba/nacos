package com.alibaba.nacos.plugin.control.tps.diamond;

import com.alibaba.nacos.plugin.control.tps.mse.FlowedLocalSimpleCountRuleBarrier;

import java.util.concurrent.TimeUnit;

public class FlowedRedisCountRuleBarrier extends FlowedLocalSimpleCountRuleBarrier {
    
    
    public FlowedRedisCountRuleBarrier(String name, String pattern, TimeUnit period, String model) {
        super(name, pattern, period, model);
    }
    
    
    
}
