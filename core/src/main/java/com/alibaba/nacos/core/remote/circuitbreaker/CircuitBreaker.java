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

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CircuitBreaker.
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class CircuitBreaker {

    public static final String DEFAULT_RULE = "default";

    private String currentRule;

    private ConfigLoader configLoader;

    private final ConcurrentHashMap<String, CircuitBreakerStatus> POINT_TO_STATUS_MAP = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, CircuitBreakerConfig> RULE_CONFIG_MAP = new ConcurrentHashMap<>();

    CircuitBreaker() {

    }

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyForStrategy(String pointName) {
        if (!POINT_TO_STATUS_MAP.containsKey(pointName)) {
            registerPoint(pointName, DEFAULT_RULE);
        }
        CircuitBreakerConfig config = RULE_CONFIG_MAP.get(StringUtils.isEmpty(currentRule) ? DEFAULT_RULE : currentRule);

        ServiceLoader<CircuitBreakerRule> circuitBreakers = ServiceLoader.load(CircuitBreakerRule.class);

        // SPI mechanism to load rule implementation
        // Can choose either check for Tps or for network flow control or both
        for (CircuitBreakerRule circuitBreakerRule : circuitBreakers) {
            if (circuitBreakerRule.getRuleName().equals(config.getStrategyName())) {

                // Get current point status and use it in applyForTps and applyForFlowControl methods
                CircuitBreakerStatus pointStatus = POINT_TO_STATUS_MAP.get(pointName);

                return circuitBreakerRule.applyForTps(pointStatus, config)
                        && circuitBreakerRule.applyForFlowControl(pointStatus, config);
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
    public static boolean applyForStrategy(String pointName, String strategyName) {
        return true;
    }


    /**
     * Init the current global CircuitBreaker. Load configs from local or DB.
     */
    private  void init() {
        RULE_CONFIG_MAP.put(DEFAULT_RULE, new CircuitBreakerConfig());
        loadConfig();
    }

    /**
     * Register new point.
     * @param  pointName entry point name or class name
     * @param  ruleName the specific circuit break strategy name
     */
    public  void registerPoint(String pointName, String ruleName) {

        if (!POINT_TO_STATUS_MAP.containsKey(pointName)) {
            POINT_TO_STATUS_MAP.put(pointName, new CircuitBreakerStatus());
        }

        if (!RULE_CONFIG_MAP.containsKey(ruleName)) {
            RULE_CONFIG_MAP.put(ruleName, new CircuitBreakerConfig());
        }
    }


    /**
     * Load config from local file or remote db.
     */
    public  void loadConfig() {
        configLoader.updateLocalConfig();
    }


    /**
     * Get the current config for a specific rule.
     */
    public  CircuitBreakerConfig getConfig(String ruleName) {
        return RULE_CONFIG_MAP.getOrDefault(ruleName, new CircuitBreakerConfig());
    }


    /**
     * Get the current status for a specific point.
     */
    public  CircuitBreakerStatus getStatus(String pointName) {
        return POINT_TO_STATUS_MAP.getOrDefault(pointName, new CircuitBreakerStatus());
    }

}