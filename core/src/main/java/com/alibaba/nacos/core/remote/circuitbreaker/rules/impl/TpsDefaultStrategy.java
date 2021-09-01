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
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerMonitor;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerStrategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default rule for circuit breaker.
 * @author czf
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class TpsDefaultStrategy extends CircuitBreakerStrategy {

    private final Map<String, TpsMonitor> pointToMonitorMap = new ConcurrentHashMap<>();

    private final Map<String, TpsConfig> pointToConfigMap = new ConcurrentHashMap<>();

    @Override
    public String getRuleName() {
        return "default";
    }

    /**
     * Check for tps condition for the current point.
     * TODO: implement this method
     */
    @Override
    public boolean applyForTps(String pointName) {
        if (!pointToMonitorMap.containsKey(pointName)) {
            if (pointToConfigMap.containsKey(pointName)) {
                TpsConfig config = pointToConfigMap.get(pointName);
                pointToMonitorMap.put(pointName, new TpsMonitor(pointName, config));
            } else {
                pointToMonitorMap.put(pointName, new TpsMonitor(pointName));
            }
        }
        //  TpsConfig config = pointConfigMap.getOrDefault(
        TpsMonitor pointMonitor = pointToMonitorMap.get(pointName);
        return pointMonitor.applyTps();
    }

    @Override
    public boolean applyRule(String pointName, CircuitBreakerConfig config) {
        return true;
    }

    @Override
    public TpsDefaultStrategy getStrategy() {
        return this;
    }

    @Override
    public void updateConfig(Map<String, CircuitBreakerConfig> configMap) {
        for (Map.Entry<String, CircuitBreakerConfig> entry : configMap.entrySet()) {
            TpsConfig config = (TpsConfig) entry.getValue();
            pointToConfigMap.put(entry.getKey(), config);
            if (pointToMonitorMap.containsKey(entry.getKey())) {
                pointToMonitorMap.get(entry.getKey()).setConfig(config);
            }
        }
    }

    @Override
    public void updateConfig(Map<String, CircuitBreakerConfig> configMap, String pointName) {
        TpsConfig config = (TpsConfig) configMap.get(pointName);
        pointToConfigMap.put(pointName, config);
        if (pointToMonitorMap.containsKey(pointName)) {
            pointToMonitorMap.get(pointName).setConfig(config);
        }

    }

    @Override
    public Map<String, CircuitBreakerConfig> getAllConfig() {
        return null;
    }

    @Override
    public Map<String, CircuitBreakerMonitor> getPointRecorders() {
        return null;
    }
}
