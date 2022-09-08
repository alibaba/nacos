package com.alibaba.nacos.plugin.control.tps;

import java.util.concurrent.TimeUnit;

public interface RuleBarrierCreator {
    
    /**
     * create a count for time unit period.
     *
     * @param period
     * @return
     */
    RuleBarrier createRateCount(String name, String pattern, TimeUnit period);
    
    /**
     * rate count creator name.
     *
     * @return name.
     */
    String name();
}
