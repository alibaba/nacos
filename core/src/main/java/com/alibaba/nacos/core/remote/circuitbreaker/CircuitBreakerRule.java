package com.alibaba.nacos.core.remote.circuitbreaker;

/**
 * Abstract circuit breaker rule class. User can define their own rule class to apply rules other than the default one
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月07日 22:45 PM chuzefang Exp $
 */
public abstract class CircuitBreakerRule {

    private String ruleName;

    public String getRuleName() { return ruleName; }

    /**
     * Main method for circuit breaker to apply its rule.
     *
     * @param  info current info including server status (like tps / network flow over time)
     * @param  config the specific circuit breaker config for this rule
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public abstract boolean applyRule(CircuitBreakerInfo info, CircuitBreakerConfig config);
}
