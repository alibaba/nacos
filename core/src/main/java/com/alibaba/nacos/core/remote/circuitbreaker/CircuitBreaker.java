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

    private String currentRuleName = DEFAULT_RULE;

    private CircuitBreakerStrategy currentRule;

    private ConfigLoader configLoader;

    private final ConcurrentHashMap<String, CircuitBreakerConfig> pointConfigMap = new ConcurrentHashMap<>();

    CircuitBreaker() {
        init();
    }

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyForStrategy(String pointName) {

        return currentRule.applyForTps(pointName);
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
    private void init() {
        pointConfigMap.put(DEFAULT_RULE, new CircuitBreakerConfig());
        configLoader = new ConfigLoader();
        loadConfig();

        ServiceLoader<CircuitBreakerStrategy> circuitBreakers = ServiceLoader.load(CircuitBreakerStrategy.class);
        System.out.println(circuitBreakers);

        // SPI mechanism to load rule implementation as current circuit breaker strategy
        for (CircuitBreakerStrategy circuitBreakerStrategy : circuitBreakers) {
            System.out.println(circuitBreakerStrategy.getRuleName());
            if (circuitBreakerStrategy.getRuleName().equals(DEFAULT_RULE)) {

                this.currentRule = circuitBreakerStrategy.getStrategy();

            }
        }

    }

    /**
     * Register new point.
     * TODO: add register logic
     * @param  pointName entry point name or class name
     * @param  ruleName the specific circuit break strategy name
     */
    public void registerPoint(String pointName, String ruleName) {

        if (!pointConfigMap.containsKey(ruleName)) {
            pointConfigMap.put(ruleName, new CircuitBreakerConfig());
        }
    }

    /**
     * Load config from local file or remote db.
     */
    public  void loadConfig() {
        configLoader.updateLocalConfig();
    }

    /**
     * Get the current config for a specific point.
     */
    public  CircuitBreakerConfig getConfig(String pointName) {
        return pointConfigMap.getOrDefault(pointName, new CircuitBreakerConfig());
    }


}