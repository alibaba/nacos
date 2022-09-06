package com.alibaba.nacos.plugin.control.tps;

import java.util.concurrent.TimeUnit;

public abstract class RateCounter {
    
    
    public RateCounter() {
    
    }
    
    private TimeUnit period;
    
    public RateCounter(TimeUnit period) {
        this.period = period;
    }
    
    public TimeUnit getPeriod() {
        return period;
    }
    
    /**
     * @param timestamp
     * @param count
     * @return
     */
    abstract public void add(long timestamp, long count);
    
    /**
     * @param timestamp
     * @param count
     * @param upLimit
     * @return
     */
    abstract public boolean tryAdd(long timestamp, long count, long upLimit);
    
    /**
     * minus count
     *
     * @param count
     * @param timestamp
     */
    abstract public void minus(long timestamp, long count);
    
    /**
     * @param timestamp
     * @return
     */
    abstract public long getCount(long timestamp);
    
    
}
