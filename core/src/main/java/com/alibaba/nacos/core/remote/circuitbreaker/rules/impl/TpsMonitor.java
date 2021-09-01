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

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerMonitor;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.remote.control.MonitorKeyMatcher;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsMonitor extends CircuitBreakerMonitor {

    public static final int DEFAULT_RECORD_SIZE = 10;

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private long startTime;

    private String pointName;

    private TpsRecorder tpsRecorder;

    private TpsConfig pointConfig;

    public Map<String, TpsRecorder> monitorKeysRecorder = new HashMap<>();

    public Map<String, TpsConfig> monitorKeysConfig = new HashMap<>();

    public TpsMonitor(String pointName) {
        this(pointName, new TpsConfig());
    }

    public TpsMonitor(String pointName, TpsConfig config) {
        // trim to second,uniform all tps control.
        this.startTime = getTrimMillsOfSecond(System.currentTimeMillis());
        this.pointName = pointName;
        this.tpsRecorder = new TpsRecorder(startTime, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                DEFAULT_RECORD_SIZE);
        this.pointConfig = config;
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return mills of second.
     */
    public static long getTrimMillsOfSecond(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.parseLong(substring + "000");
        
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return minis of minute.
     */
    public static long getTrimMillsOfMinute(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.parseLong(Long.parseLong(substring) / 60 * 60 + "000");
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return mills of hour.
     */
    public static long getTrimMillsOfHour(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.parseLong(Long.parseLong(substring) / (60 * 60) * (60 * 60) + "000");
    }
    
    /**
     * get format string "2021-01-16 17:20:21" of timestamp.
     *
     * @param timeStamp timestamp milliseconds.
     * @return datetime string.
     */
    public static String getTimeFormatOfSecond(long timeStamp) {
        return new SimpleDateFormat(DATETIME_PATTERN).format(new Date(timeStamp));
    }
    
    private void stopAllMonitorClient() {
        monitorKeysRecorder.clear();
        monitorKeysConfig.clear();
    }

    private void clearAllTpsConfigs() {
        pointConfig.setIsActive(false);
        monitorKeysConfig.clear();
        monitorKeysRecorder.clear();
    }
    
    /**
     * increase tps.
     *
     * @param monitorKeys monitorKeys.
     * @return check current tps is allowed.
     */
    public boolean applyTps(String connectionId, List<MonitorKey> monitorKeys) {
        
        long now = System.currentTimeMillis();
        TpsRecorder.TpsSlot currentTps = tpsRecorder.createSlotIfAbsent(now);
        
        //1.check monitor keys.
        List<TpsRecorder.SlotCountHolder> passedSlots = new ArrayList<>();
        for (MonitorKey monitorKey : monitorKeys) {
            for (Map.Entry<String, TpsRecorder> entry : monitorKeysRecorder.entrySet()) {
                if (MonitorKeyMatcher.matchWithType(entry.getKey(), monitorKey.build())) {
                    TpsRecorder tpsRecorderKey = entry.getValue();
                    TpsRecorder.TpsSlot currentKeySlot = tpsRecorderKey.createSlotIfAbsent(now);

                    // get max count status from config instead of directly from the TpsRecorder

                    long maxTpsCount = tpsRecorderKey.getMaxCount();
                    com.alibaba.nacos.core.remote.circuitbreaker.rules.impl.TpsRecorder.SlotCountHolder countHolder = currentKeySlot.getCountHolder(monitorKey.build());
                    boolean overLimit = maxTpsCount >= 0 && countHolder.count.longValue() >= maxTpsCount;
                    if (overLimit) {
                        Loggers.TPS_CONTROL_DETAIL
                                .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorModel={},maxTps={}",
                                        connectionId, this.getPointName(), entry.getKey(),
                                        tpsRecorderKey.getMonitorType(), maxTpsCount + "/" + tpsRecorderKey.period);
                        if (tpsRecorderKey.isInterceptMode()) {
                            currentKeySlot.getCountHolder(monitorKey.build()).interceptedCount.incrementAndGet();
                            currentTps.getCountHolder(monitorKey.build()).interceptedCount.incrementAndGet();
                            return false;
                        }
                    } else {
                        passedSlots.add(countHolder);
                    }
                }
            }
        }
        
        //2.check total tps.
        long maxTps = tpsRecorder.getMaxCount();
        boolean overLimit = maxTps >= 0 && currentTps.getCountHolder(pointName).count.longValue() >= maxTps;
        if (overLimit) {
            Loggers.TPS_CONTROL_DETAIL
                    .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorType={}", connectionId,
                            this.getPointName(), "pointRule", tpsRecorder.getMonitorType());
            if (tpsRecorder.isInterceptMode()) {
                currentTps.getCountHolder(pointName).interceptedCount.incrementAndGet();
                return false;
            }
        }
        
        currentTps.getCountHolder(pointName).count.incrementAndGet();
        for (com.alibaba.nacos.core.remote.control.TpsRecorder.SlotCountHolder passedTpsSlot : passedSlots) {
            passedTpsSlot.count.incrementAndGet();
        }
        //3.check pass.
        return true;
    }
    
    public TpsRecorder getTpsRecorder() {
        return tpsRecorder;
    }
    
    public String getPointName() {
        return pointName;
    }

    public void setConfig(TpsConfig config) { tpsConfig = config; }

    public TpsConfig getConfig() { return tpsConfig; }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    /**
     * apply tps control rule to this point.
     *
     * @param newPointConfig controlRule.
     * @param newKeyMonitorConfigs monitor keys config map
     */
    public synchronized void applyRule(boolean clearAll, TpsConfig newPointConfig,
                                       Map<String, TpsConfig> newKeyMonitorConfigs) {
        
        Loggers.TPS_CONTROL.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (clearAll) {
            Loggers.TPS_CONTROL.info("Clear all tps control config ,pointName=[{}]  ", this.getPointName());
            this.clearAllTpsConfigs();
            this.stopAllMonitorClient();
            return;
        }
        
        //2.check point rule.
        if (newPointConfig == null) {
            Loggers.TPS_CONTROL.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            pointConfig.setIsActive(false);
        } else {
            Loggers.TPS_CONTROL.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.pointConfig.getMaxCount(), newPointConfig.getMaxCount(), this.pointConfig.getMonitorType(),
                    newPointConfig.getMonitorType());
            this.pointConfig = newPointConfig;
        }
        
        //3.check monitor key rules.
        // 3.1 clear all monitor keys.
        if (newKeyMonitorConfigs == null || newKeyMonitorConfigs.isEmpty()) {
            Loggers.TPS_CONTROL
                    .info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.stopAllMonitorClient();
        } else {
            Map<String, TpsConfig> monitorKeysConfigCurrent = this.monitorKeysConfig;
            Map<String, TpsRecorder> monitorKeysRecorderCurrent =  this.monitorKeysRecorder;
            
            for (Map.Entry<String, TpsConfig> newMonitorConfig : newKeyMonitorConfigs.entrySet()) {
                if (newMonitorConfig.getValue() == null) {
                    continue;
                }
                boolean checkPattern = newMonitorConfig.getKey() != null;
                if (!checkPattern) {
                    Loggers.TPS_CONTROL.info("Invalid monitor rule, pointName=[{}] ,monitorRule={} ,Ignore this.",
                            this.getPointName(), newMonitorConfig.getKey());
                    continue;
                }
                TpsConfig newConfig = newMonitorConfig.getValue();

                //update rule.
                if (monitorKeysConfigCurrent.containsKey(newMonitorConfig.getKey())) {
                    TpsConfig oldConfig = monitorKeysConfigCurrent.get(newMonitorConfig.getKey());
                    Loggers.TPS_CONTROL
                            .info("Update  point  control rule for client ip ,pointName=[{}],monitorKey=[{}],original maxTps={}"
                                            + ", new maxTps={},original monitorType={}, new monitorType={}, ",
                                    this.getPointName(), newMonitorConfig.getKey(), oldConfig.getMaxCount(),
                                    newConfig.getMaxCount(), oldConfig.getMonitorType(), newConfig.getMonitorType());
                    
                    if (!Objects.equals(oldConfig.getPeriod(), newConfig.getPeriod()) || !Objects
                            .equals(oldConfig.getModel(), newConfig.getModel())) {

                        TpsRecorder tpsRecorderNew = new TpsRecorder(startTime, newConfig.getPeriod(), newConfig.getModel(),
                                DEFAULT_RECORD_SIZE);
                        monitorKeysRecorderCurrent.put(newMonitorConfig.getKey(), tpsRecorderNew);
                    }
                    monitorKeysConfigCurrent.put(newMonitorConfig.getKey(), newConfig);
                    
                } else {
                    Loggers.TPS_CONTROL
                            .info("Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new maxTps={}, new monitorType={}, ",
                                    this.getPointName(), newMonitorConfig.getKey(), newMonitorConfig.getValue().getMaxCount(),
                                    newMonitorConfig.getValue().getMonitorType());
                    // add config & new recorder
                    TpsRecorder tpsRecorderNew = new TpsRecorder(startTime, newConfig.getPeriod(), newConfig.getModel(),
                            DEFAULT_RECORD_SIZE);

                    monitorKeysRecorderCurrent.put(newMonitorConfig.getKey(), tpsRecorderNew);
                    monitorKeysConfigCurrent.put(newMonitorConfig.getKey(), newConfig);
                }
            }
            
            //delete rule.
            Iterator<Map.Entry<String, TpsConfig>> iteratorCurrent = monitorKeysConfigCurrent.entrySet().iterator();
            while (iteratorCurrent.hasNext()) {
                Map.Entry<String, TpsConfig> next1 = iteratorCurrent.next();
                if (!newKeyMonitorConfigs.containsKey(next1.getKey())) {
                    Loggers.TPS_CONTROL.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]",
                            this.getPointName(), next1.getKey());
                    // remove config & its related recorder
                    monitorKeysRecorderCurrent.remove(next1.getKey());
                    iteratorCurrent.remove();
                }
            }
            
        }
        
    }
    
}
