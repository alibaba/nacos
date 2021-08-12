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

package com.alibaba.nacos.core.remote.circuitbreaker.rules.impl;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerStatus;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRule;

/**
 * Default rule for circuit breaker.
 * @author czf
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class TpsDefaultRule extends CircuitBreakerRule {

    @Override
    public String getRuleName() {
        return "default";
    }

    /**
     * Check for tps condition for the current point.
     * TODO: implement this method
     */
    @Override
    public boolean applyForTps(CircuitBreakerStatus status, CircuitBreakerConfig config) {
        System.out.println("TpsDefaultRule#applyForTps");
        return true;
    }

    /**
     * Check for network flow condition for the current point.
     * TODO: implement this method
     */
    @Override
    public boolean applyForFlowControl(CircuitBreakerStatus status, CircuitBreakerConfig config) {
        System.out.println("TpsDefaultRule#applyForFLowControl");
        return true;
    }
}
