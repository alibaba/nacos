package com.alibaba.nacos.plugin.control.tps;

import java.util.concurrent.TimeUnit;

public interface RuleBarrierCreator {
    
    /**
     * create a count for time unit period.
     *
     * @param pointName pointName.
     * @param ruleName  ruleName.
     * @param period    period.
     * @return
     */
    RuleBarrier createRuleBarrier(String pointName, String ruleName, TimeUnit period);
    
    /**
     * rate count creator name.
     *
     * @return name.
     */
    String name();
}
