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

package com.alibaba.nacos.core.remote.circuitbreaker.rule.tps;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerMonitor;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorType;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.remote.control.MonitorKeyMatcher;
import org.apache.commons.collections.MapUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * tps control point.
 *
 * @author chuzefang
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM chuzefang Exp $
 */
public class TpsMonitor extends CircuitBreakerMonitor {

    public static final int DEFAULT_RECORD_SIZE = 10;

    private TpsRecorder tpsRecorder;

    public Map<String, TpsRecorder> monitorKeysRecorder = new HashMap<>();

    public Map<String, TpsConfig> monitorKeysConfig = new HashMap<>();

    public TpsMonitor(String pointName) {
        this(pointName, new TpsConfig());
    }

    public TpsMonitor(String pointName, TpsConfig config) {
        // trim to second,uniform all tps control.
        this.startTime = getTrimMillsOfSecond(System.currentTimeMillis());
        this.pointName = pointName;
        this.tpsRecorder = new TpsRecorder(pointName, startTime, DEFAULT_RECORD_SIZE, config);
    }

    @Override
    public void createAndPutNewRecorder(String key, CircuitBreakerConfig config) {
        this.monitorKeysRecorder.put(key, new TpsRecorder(pointName, startTime, DEFAULT_RECORD_SIZE, (TpsConfig) config));
    }

    @Override
    public Map<String, CircuitBreakerRecorder> getMonitorKeyRecorders() {
        return this.monitorKeysRecorder.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void stopAllMonitorClient() {
        monitorKeysRecorder.clear();
        monitorKeysConfig.clear();
    }

    @Override
    public void clearAllTpsConfigs() {
        // disable current config for current point
        tpsRecorder.getConfig().setIsActive(false);
        monitorKeysConfig.clear();
        monitorKeysRecorder.clear();
    }

    private boolean isInterceptMode(String monitorType) {
        return MonitorType.INTERCEPT.getType().equals(monitorType);
    }
    /**
     * increase tps.
     *
     * @param monitorKeys monitorKeys.
     * @return check current tps is allowed.
     */
    public boolean applyTps(List<MonitorKey> monitorKeys) {
        long now = System.currentTimeMillis();
        CircuitBreakerRecorder.Slot currentTps = tpsRecorder.createSlotIfAbsent(now);

        // Find connectionId for the current monitorKeys
        String connectionId = getConnectionId(monitorKeys);
        
        //1.check monitor keys.
        List<CircuitBreakerRecorder.SlotCountHolder> passedSlots = new ArrayList<>();
        for (MonitorKey monitorKey : monitorKeys) {
            for (Map.Entry<String, TpsRecorder> entry : monitorKeysRecorder.entrySet()) {

                // ConnectionIdMonitorKey should not be included
                if (!CONNECTION_ID.equals(monitorKey.getType())
                        && MonitorKeyMatcher.matchWithType(entry.getKey(), monitorKey.build())) {
                    TpsRecorder tpsRecorderKey = entry.getValue();
                    CircuitBreakerRecorder.Slot currentKeySlot = tpsRecorderKey.createSlotIfAbsent(now);

                    // get max count status from config instead of directly from the TpsRecorder
                    TpsConfig config = tpsRecorderKey.getConfig();
                    long maxTpsCount = config.getMaxCount();
                    CircuitBreakerRecorder.SlotCountHolder countHolder = currentKeySlot.getCountHolder(pointName);
                    boolean overLimit = maxTpsCount >= 0 && countHolder.count.longValue() >= maxTpsCount;
                    if (overLimit) {
                        Loggers.TPS_CONTROL_DETAIL
                                .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorModel={},maxTps={}",
                                        connectionId, this.getPointName(), entry.getKey(),
                                        config.getMonitorType(), maxTpsCount + "/" + config.getPeriod());
                        if (isInterceptMode(config.getMonitorType())) {
                            currentKeySlot.getCountHolder(pointName).interceptedCount.incrementAndGet();
                            currentTps.getCountHolder(pointName).interceptedCount.incrementAndGet();
                            return false;
                        }
                    } else {
                        passedSlots.add(countHolder);
                    }
                }
            }
        }
        
        //2.check total tps.
        long maxTps = tpsRecorder.getConfig().getMaxCount();
        boolean overLimit = maxTps >= 0 && currentTps.getCountHolder(pointName).count.longValue() >= maxTps;

        if (overLimit) {
            Loggers.TPS_CONTROL_DETAIL
                    .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorType={}", connectionId,
                            this.getPointName(), "pointRule", tpsRecorder.getConfig().getMonitorType());
            if (isInterceptMode(tpsRecorder.getConfig().getMonitorType())) {
                currentTps.getCountHolder(pointName).interceptedCount.incrementAndGet();
                return false;
            }
        }
        currentTps.getCountHolder(pointName).count.incrementAndGet();
        for (CircuitBreakerRecorder.SlotCountHolder passedTpsSlot : passedSlots) {
            passedTpsSlot.count.incrementAndGet();
        }
        //3.check pass.
        return true;
    }
    
    public TpsRecorder getTpsRecorder() {
        return tpsRecorder;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    private String getConnectionId(List<MonitorKey> monitorKeys) {
        for (MonitorKey monitorKey : monitorKeys) {
            if (CONNECTION_ID.equals(monitorKey.getType())) {
                return monitorKey.getKey();
            }
        }
        return "";
    }

    @Override
    public CircuitBreakerRecorder getPointRecorder() {
        return tpsRecorder;
    }

    @Override
    public Map<String, CircuitBreakerRecorder> getMonitorKeysRecorder() {
        if (MapUtils.isNotEmpty(monitorKeysRecorder)) {
            return monitorKeysRecorder.entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return new HashMap<>();
    }

    @Override
    public CircuitBreakerRecorder getCurrentRecorder() {
        return this.tpsRecorder;
    }
}

