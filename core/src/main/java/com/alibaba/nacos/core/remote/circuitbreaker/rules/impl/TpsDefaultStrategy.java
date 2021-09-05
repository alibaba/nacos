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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerMonitor;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.nacos.core.remote.control.ClientIpMonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.MapUtils;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default rule for circuit breaker.
 * @author czf
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class TpsDefaultStrategy extends CircuitBreakerStrategy {

    private final Map<String, TpsMonitor> pointToMonitorMap = new ConcurrentHashMap<>();

    @Override
    public String getRuleName() {
        return "default";
    }

    @Override
    public void registerPoint(String pointName) {
        pointToMonitorMap.putIfAbsent(pointName, new TpsMonitor(pointName));
    }

    /**
     //     * Check for tps condition for the current point.
     //     * TODO: implement this method
     //     */
    @Override
    public boolean applyStrategy(String pointName, String connectionId, List<MonitorKey> monitorKeyList) {
        System.out.println("here");
        System.out.println(pointToMonitorMap.containsKey(pointName));
        if (pointToMonitorMap.containsKey(pointName)) {
            TpsMonitor pointMonitor = pointToMonitorMap.get(pointName);
            return pointMonitor.applyTps(connectionId, monitorKeyList);
        }
        return true;
    }

    @Override
    public boolean applyStrategyForClientIp(String pointName, String connectionId, String clientIp) {
        if (pointToMonitorMap.containsKey(pointName)) {
            TpsMonitor pointMonitor = pointToMonitorMap.get(pointName);
            return pointMonitor.applyTps(connectionId,  Arrays.asList(new ClientIpMonitorKey(clientIp)));
        }
        return true;
    }

    @Override
    public TpsDefaultStrategy getStrategy() {
        return this;
    }

    @Override
    public void applyRule(String pointName, CircuitBreakerConfig config,
                                     Map<String, CircuitBreakerConfig> keyConfigMap) {

        TpsConfig castedPointConfig = (TpsConfig) config;

        // implicit cast from CircuitBreakerConfig (parent class) to TpsConfig subclass
        Map<String, TpsConfig> castedKeyConfigMap = keyConfigMap.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (TpsConfig) e.getValue()));

        if (pointToMonitorMap.containsKey(pointName)) {
            TpsMonitor pointMonitor = pointToMonitorMap.get(pointName);
            pointMonitor.applyRule(false, castedPointConfig, castedKeyConfigMap);
        } else {
            TpsMonitor newMonitor = new TpsMonitor(pointName, castedPointConfig);
            newMonitor.applyRule(false, castedPointConfig, castedKeyConfigMap);
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

    @Override
    public CircuitBreakerConfig deserializePointConfig(String content) {
        return StringUtils.isBlank(content) ? new TpsConfig()
                : JacksonUtils.toObj(content, TpsConfig.class);
    }

    @Override
    public Map<String, CircuitBreakerConfig> deserializeMonitorKeyConfig(String content) {
        TypeReference<Map<String,TpsConfig>> typeRef
                = new TypeReference<Map<String, TpsConfig>>() {};
        Map<String,TpsConfig> configMap = StringUtils.isBlank(content) ? new HashMap<>()
                : JacksonUtils.toObj(content, typeRef);

        Map<String,CircuitBreakerConfig> retMap = new HashMap<>();
        if (MapUtils.isNotEmpty(configMap)) {
            retMap = configMap.entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return retMap;
    }

}
