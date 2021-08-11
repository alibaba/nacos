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

/**
 * Abstract circuit breaker rule class. User can define their own rule class to apply rules other than the default one
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月07日 22:45 PM chuzefang Exp $
 */
public class CircuitBreakerRule {

    private String ruleName;

    public String getRuleName() {
        return ruleName;
    }

    /**
     * Main method for circuit breaker to apply tps control rule.
     *
     * @param  config the specific circuit breaker config for this rule
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyForTps(CircuitBreakerStatus status, CircuitBreakerConfig config) {
        return true;
    }

    /**
     * Main method for circuit breaker to apply network flow control rule.
     *
     * @param  config the specific circuit breaker config for this rule
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyForFlowControl(CircuitBreakerStatus status, CircuitBreakerConfig config) {
        return true;
    }

}
