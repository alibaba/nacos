package com.alibaba.nacos.plugin.control.tps.diamond;

import com.alibaba.nacos.plugin.control.tps.nacos.RateCounter;

import java.util.concurrent.TimeUnit;

public class ClusterCountRateCounter extends RateCounter {
    
    public ClusterCountRateCounter(String name, TimeUnit period) {
        super(name, period);
    }
    
    @Override
    public void add(long timestamp, long count) {
    
    }
    
    @Override
    public boolean tryAdd(long timestamp, long count, long upLimit) {
        return false;
    }
    
    @Override
    public void minus(long timestamp, long count) {
    
    }
    
    @Override
    public long getCount(long timestamp) {
        return 0;
    }
}
