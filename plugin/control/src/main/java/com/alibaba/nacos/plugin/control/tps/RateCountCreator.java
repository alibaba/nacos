package com.alibaba.nacos.plugin.control.tps;

import java.util.concurrent.TimeUnit;

public interface RateCountCreator {
    
    /**
     * create a count for time unit period.
     *
     * @param period
     * @return
     */
    RateCounter createRateCount(TimeUnit period);
    
    /**
     * rate count creator name.
     *
     * @return name.
     */
    String name();
}
