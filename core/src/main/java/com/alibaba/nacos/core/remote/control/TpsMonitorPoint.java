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

package com.alibaba.nacos.core.remote.control;

import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.core.utils.Loggers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsMonitorPoint {
    
    public static final int DEFAULT_RECORD_SIZE = 10;
    
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    
    private long startTime;
    
    private String pointName;
    
    private TpsRecorder tpsRecorder;
    
    public Map<String, TpsRecorder> monitorKeysRecorder = new HashMap<String, TpsRecorder>();
    
    public TpsMonitorPoint(String pointName) {
        this(pointName, -1, "monitor");
    }
    
    public TpsMonitorPoint(String pointName, int maxTps, String monitorType) {
        // trim to second,uniform all tps control.
        this.startTime = getTrimMillsOfSecond(System.currentTimeMillis());
        this.pointName = pointName;
        this.tpsRecorder = new TpsRecorder(startTime, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                DEFAULT_RECORD_SIZE);
        this.tpsRecorder.setMaxCount(maxTps);
        this.tpsRecorder.setMonitorType(monitorType);
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
                    long maxTpsCount = tpsRecorderKey.getMaxCount();
                    TpsRecorder.SlotCountHolder countHolder = currentKeySlot.getCountHolder(monitorKey.build());
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
        for (TpsRecorder.SlotCountHolder passedTpsSlot : passedSlots) {
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
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    /**
     * apply tps control rule to this point.
     *
     * @param newControlRule controlRule.
     */
    public synchronized void applyRule(TpsControlRule newControlRule) {
        
        Loggers.TPS_CONTROL.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (newControlRule == null) {
            Loggers.TPS_CONTROL.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            this.tpsRecorder.clearLimitRule();
            this.stopAllMonitorClient();
            return;
        }
        
        //2.check point rule.
        TpsControlRule.Rule newPointRule = newControlRule.getPointRule();
        if (newPointRule == null) {
            Loggers.TPS_CONTROL.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            this.tpsRecorder.clearLimitRule();
        } else {
            Loggers.TPS_CONTROL.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.tpsRecorder.getMaxCount(), newPointRule.maxCount, this.tpsRecorder.getMonitorType(),
                    newPointRule.monitorType);
            
            this.tpsRecorder.setMaxCount(newPointRule.maxCount);
            this.tpsRecorder.setMonitorType(newPointRule.monitorType);
        }
        
        //3.check monitor key rules.
        Map<String, TpsControlRule.Rule> newMonitorKeyRules = newControlRule.getMonitorKeyRule();
        //3.1 clear all monitor keys.
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            Loggers.TPS_CONTROL
                    .info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.stopAllMonitorClient();
        } else {
            Map<String, TpsRecorder> monitorKeysRecorderCurrent = this.monitorKeysRecorder;
            
            for (Map.Entry<String, TpsControlRule.Rule> newMonitorRule : newMonitorKeyRules.entrySet()) {
                if (newMonitorRule.getValue() == null) {
                    continue;
                }
                boolean checkPattern = newMonitorRule.getKey() != null;
                if (!checkPattern) {
                    Loggers.TPS_CONTROL.info("Invalid monitor rule, pointName=[{}] ,monitorRule={} ,Ignore this.",
                            this.getPointName(), newMonitorRule.getKey());
                    continue;
                }
                TpsControlRule.Rule newRule = newMonitorRule.getValue();
                if (newRule.period == null) {
                    newRule.period = TimeUnit.SECONDS;
                }
                
                if (newRule.model == null) {
                    newRule.model = TpsControlRule.Rule.MODEL_FUZZY;
                }
                
                //update rule.
                if (monitorKeysRecorderCurrent.containsKey(newMonitorRule.getKey())) {
                    TpsRecorder tpsRecorder = monitorKeysRecorderCurrent.get(newMonitorRule.getKey());
                    Loggers.TPS_CONTROL
                            .info("Update  point  control rule for client ip ,pointName=[{}],monitorKey=[{}],original maxTps={}"
                                            + ", new maxTps={},original monitorType={}, new monitorType={}, ",
                                    this.getPointName(), newMonitorRule.getKey(), tpsRecorder.getMaxCount(),
                                    newRule.maxCount, tpsRecorder.getMonitorType(), newRule.monitorType);
                    
                    if (!Objects.equals(tpsRecorder.period, newRule.period) || !Objects
                            .equals(tpsRecorder.getModel(), newRule.model)) {
                        TpsRecorder tpsRecorderNew = new TpsRecorder(startTime, newRule.period, newRule.model,
                                DEFAULT_RECORD_SIZE);
                        tpsRecorderNew.setMaxCount(newRule.maxCount);
                        tpsRecorderNew.setMonitorType(newRule.monitorType);
                        monitorKeysRecorderCurrent.put(newMonitorRule.getKey(), tpsRecorderNew);
                    } else {
                        tpsRecorder.setMaxCount(newRule.maxCount);
                        tpsRecorder.setMonitorType(newRule.monitorType);
                    }
                    
                } else {
                    Loggers.TPS_CONTROL
                            .info("Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new maxTps={}, new monitorType={}, ",
                                    this.getPointName(), newMonitorRule.getKey(), newMonitorRule.getValue().maxCount,
                                    newMonitorRule.getValue().monitorType);
                    // add rule
                    TpsRecorder tpsRecorderAdd = new TpsRecorder(startTime, newRule.period, newRule.model,
                            DEFAULT_RECORD_SIZE);
                    tpsRecorderAdd.setMaxCount(newRule.maxCount);
                    tpsRecorderAdd.setMonitorType(newRule.monitorType);
                    monitorKeysRecorderCurrent.put(newMonitorRule.getKey(), tpsRecorderAdd);
                }
            }
            
            //delete rule.
            Iterator<Map.Entry<String, TpsRecorder>> iteratorCurrent = monitorKeysRecorderCurrent.entrySet().iterator();
            while (iteratorCurrent.hasNext()) {
                Map.Entry<String, TpsRecorder> next1 = iteratorCurrent.next();
                if (!newMonitorKeyRules.containsKey(next1.getKey())) {
                    Loggers.TPS_CONTROL.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]",
                            this.getPointName(), next1.getKey());
                    iteratorCurrent.remove();
                }
            }
            
        }
        
    }
    
}
