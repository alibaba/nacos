package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RateCountCreator;
import com.alibaba.nacos.plugin.control.tps.RateCounter;

import java.util.concurrent.TimeUnit;

public class SimpleCountRateCounterCreator implements RateCountCreator {
    
    @Override
    public RateCounter createRateCount(TimeUnit period) {
        return new SimpleCountRateCounter(period);
    }
    
    @Override
    public String name() {
        return "nacos";
    }
}
