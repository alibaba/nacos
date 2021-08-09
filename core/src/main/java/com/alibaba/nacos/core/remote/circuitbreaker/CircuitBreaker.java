/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.remote.circuitbreaker;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CircuitBreaker.
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class CircuitBreaker {

    public static final String DEFAULT_RULE = "default";

    private static final ConcurrentHashMap<String, String> POINT_TO_RULE_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, CircuitBreakerConfig> RULE_CONFIG_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, CircuitBreakerInfo> RULE_INFO_MAP = new ConcurrentHashMap<>();

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public static boolean check(String pointName) {
        if (!POINT_TO_RULE_MAP.contains(pointName)) {
            registerPoint(pointName, DEFAULT_RULE);
        }
        CircuitBreakerConfig config = RULE_CONFIG_MAP.get(POINT_TO_RULE_MAP.get(pointName));

        ServiceLoader<CircuitBreakerRule> circuitBreakers = ServiceLoader.load(CircuitBreakerRule.class);

        // SPI mechanism to load rule implementation
        for (CircuitBreakerRule circuitBreakerRule : circuitBreakers) {
            if (circuitBreakerRule.getRuleName().equals(config.getStrategyName())) {

                // find info from infoMap
                CircuitBreakerInfo info;
                if (RULE_INFO_MAP.containsKey(circuitBreakerRule.getRuleName())) {
                    info = RULE_INFO_MAP.get(circuitBreakerRule.getRuleName());
                } else {
                    info = RULE_INFO_MAP.get(DEFAULT_RULE);
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

    /**
     * Register new point.
     * @param  pointName entry point name or class name
     * @param  ruleName the specific circuit break strategy name
     */
    public static void registerPoint(String pointName, String ruleName) {

        POINT_TO_RULE_MAP.put(pointName, ruleName);

        if (!RULE_CONFIG_MAP.containsKey(ruleName)) {
            RULE_CONFIG_MAP.put(ruleName, new CircuitBreakerConfig());
        }
        if (!RULE_INFO_MAP.containsKey(ruleName)) {
            RULE_INFO_MAP.put(ruleName, new CircuitBreakerInfo());
        }

    }
}