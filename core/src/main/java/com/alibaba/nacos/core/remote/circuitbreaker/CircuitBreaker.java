package com.alibaba.nacos.core.remote.circuitbreaker;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chuzefang
 */
public class CircuitBreaker {

    public static final String DEFAULT_RULE = "default";

    /**
     * registered points to strategy name mapping
     */
    private static final ConcurrentHashMap<String, String> pointToStrategyMap = new ConcurrentHashMap<>();

    /**
     * strategy name to config mapping
     */
    private static final ConcurrentHashMap<String, CircuitBreakerConfig> ruleConfigMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, CircuitBreakerInfo> ruleInfoMap = new ConcurrentHashMap<>();


    private void init() {
    }


    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public static boolean check(String pointName) {
        if (!pointToStrategyMap.contains(pointName)) {
            registerPoint(pointName, DEFAULT_RULE);
        }
        CircuitBreakerConfig config = ruleConfigMap.get(pointToStrategyMap.get(pointName));

        ServiceLoader<CircuitBreakerRule> circuitBreakers = ServiceLoader.load(CircuitBreakerRule.class);

        // SPI mechanism to load rule implementation
        for (CircuitBreakerRule circuitBreakerRule : circuitBreakers) {
            if (circuitBreakerRule.getRuleName().equals(config.getStrategyName())) {

                // find info from infoMap
                CircuitBreakerInfo info;
                if (ruleInfoMap.containsKey(circuitBreakerRule.getRuleName())) {
                    info = ruleInfoMap.get(circuitBreakerRule.getRuleName());
                } else {
                    info = ruleInfoMap.get(DEFAULT_RULE);
                }
                return circuitBreakerRule.applyRule(info, config);
            }
        }
        return true;
    }

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name
     * @param  strategyName the specific circuit break strategy that client wants to apply for the current point
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public static boolean check(String pointName, String strategyName) {
        return true;
    }


    public static void registerPoint(String pointName, String ruleName) {

        pointToStrategyMap.put(pointName, ruleName);

        if (!ruleInfoMap.containsKey(ruleName)) {
            ruleInfoMap.put(ruleName, new CircuitBreakerInfo());
        }
        if (!ruleConfigMap.containsKey(ruleName)) {
            ruleConfigMap.put(ruleName, new CircuitBreakerConfig());
        }
    }
}