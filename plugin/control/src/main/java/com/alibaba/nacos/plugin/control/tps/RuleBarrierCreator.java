package com.alibaba.nacos.plugin.control.tps;

import java.util.concurrent.TimeUnit;

public interface RuleBarrierCreator {
    
    /**
     * create a count for time unit period.
     *
     * @param pointName pointName.
     * @param ruleName ruleName.
     * @param pattern  pattern.
     * @param period   period.
     * @param model    model.
     * @return
     */
    RuleBarrier createRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period, String model);
    
    /**
     * rate count creator name.
     *
     * @return name.
     */
    String name();
}
