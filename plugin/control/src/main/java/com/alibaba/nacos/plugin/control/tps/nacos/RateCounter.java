package com.alibaba.nacos.plugin.control.tps.nacos;

import java.util.concurrent.TimeUnit;

/**
 * abstract rate counter.
 *
 * @author zunfei.lzf
 */
public abstract class RateCounter {
    
    /**
     * rate count name.
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
     * add count for the second of timestamp.
     *
     * @param timestamp timestamp.
     * @param count     count.
     * @return
     */
    public abstract void add(long timestamp, long count);
    
    /**
     * add count for the second of timestamp with up limit.
     *
     * @param timestamp timestamp.
     * @param count     count.
     * @param upLimit   upLimit.
     * @return
     */
    public abstract boolean tryAdd(long timestamp, long count, long upLimit);
    
    /**
     * minus count.
     *
     * @param timestamp timestamp.
     * @param count     count.
     */
    public abstract void minus(long timestamp, long count);
    
    /**
     * get count of the second of timestamp.
     *
     * @param timestamp timestamp.
     * @return
     */
    public abstract long getCount(long timestamp);
    
    /**
     * get denied count of the second of timestamp.
     *
     * @param timestamp timestamp.
     * @return
     */
    public abstract long getDeniedCount(long timestamp);
    
    public String getName() {
        return name;
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfMinute(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(Long.valueOf(substring) / 60 * 60 + "000");
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfSecond(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(substring + "000");
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfHour(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(Long.valueOf(substring) / (60 * 60) * (60 * 60) + "000");
    }
}
