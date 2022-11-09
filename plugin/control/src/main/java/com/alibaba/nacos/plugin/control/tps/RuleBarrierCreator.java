package com.alibaba.nacos.plugin.control.tps;

import java.util.concurrent.TimeUnit;

public interface RuleBarrierCreator {
    
    /**
     * create a count for time unit period.
     *
     * @param name    name.
     * @param pattern pattern.
     * @param period  period.
     * @param model   model.
     * @return
     */
    RuleBarrier createRuleBarrier(String name, String pattern, TimeUnit period, String model);
    
    /**
     * rate count creator name.
     *
     * @return name.
     */
    String name();
}
