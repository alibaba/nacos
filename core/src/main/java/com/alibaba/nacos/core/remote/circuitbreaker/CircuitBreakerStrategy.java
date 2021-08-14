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

import java.util.Map;

/**
 * Abstract circuit breaker rule class. User can define their own rule class to apply rules other than the default one
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月07日 22:45 PM chuzefang Exp $
 */
public abstract class CircuitBreakerStrategy {

    private String ruleName;

    /**
     * Get the strategy name for this implementation.
     *
     * @return strategy name.
     */
    public abstract String getRuleName();

    /**
     * Main method for circuit breaker to apply tps control rule.
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public abstract boolean applyForTps(String pointName);

    /**
     * Main method to apply new config to current point.
     *
     * @param pointName point / points that apply the param config.
     * @param  config the specific circuit breaker config for this rule.
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public abstract boolean applyRule(String pointName, CircuitBreakerConfig config);

    /**
     * Get the strategy instance and save it in the container class.
     *
     * @return the specific instance with implement class.
     */
    public abstract CircuitBreakerStrategy getStrategy();


    /**
     * Get the strategy instance and save it in the container class.
     *
     * @param configMap the full or partial config map from the container
     */
    public abstract void updateConfig(Map<String, CircuitBreakerConfig> configMap);

    /**
     * Get the strategy instance and save it in the container class.
     *
     * @return the specific instance with implement class.
     */
    public abstract Map<String, CircuitBreakerConfig> getAllConfig();

    /**
     * Get the strategy instance and save it in the container class.
     *
     * @return the specific instance with implement class.
     */
    public abstract Map<String, CircuitBreakerRecorder> getPointRecorders();

}
