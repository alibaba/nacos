package com.alibaba.nacos.plugin.control.tps.nacos;

import java.util.concurrent.TimeUnit;

/**
 * abstract rate counter.
 *
 * @author zunfei.lzf
 */
public abstract class RateCounter {
    
    /**
     * rate count name;
     */
    private String name;
    
    /**
     * rate period.
     */
    private TimeUnit period;
    
    public RateCounter(String name, TimeUnit period) {
        this.name = name;
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
    
    public String getName() {
        return name;
    }
    
}
