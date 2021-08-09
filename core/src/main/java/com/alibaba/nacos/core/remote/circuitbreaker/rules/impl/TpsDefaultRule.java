package com.alibaba.nacos.core.remote.circuitbreaker.rules.impl;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerInfo;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRule;

/**
 * @author czf
 */
public class TpsDefaultRule extends CircuitBreakerRule {

    @Override
    public String getRuleName() {
        return "default";
    }

    /**
     * TODO: implement this method
     */
    @Override
    public boolean applyRule(CircuitBreakerInfo info, CircuitBreakerConfig config) {
        System.out.println("TpsDefaultRule");
        return true;
    }
}
