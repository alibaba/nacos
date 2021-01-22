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

import com.alibaba.nacos.core.utils.Loggers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsMonitorPoint {
    
    public static final int DEFAULT_RECORD_SIZE = 10;
    
    private long startTime;
    
    private String pointName;
    
    private TpsRecorder tpsRecorder;
    
    public Map<MonitorKeyMatcher, TpsRecorder> monitorKeysRecorder = new HashMap<MonitorKeyMatcher, TpsRecorder>();
    
    public TpsMonitorPoint(String pointName) {
        this(pointName, -1, "monitor");
    }
    
    public TpsMonitorPoint(String pointName, int maxTps, String monitorType) {
        // trim to second,uniform all tps control.
        this.startTime = getTrimMillsOfSecond(System.currentTimeMillis());
        this.pointName = pointName;
        this.tpsRecorder = new TpsRecorder(startTime, DEFAULT_RECORD_SIZE);
        this.tpsRecorder.setMaxTps(maxTps);
        this.tpsRecorder.setMonitorType(monitorType);
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfSecond(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(substring + "000");
        
    }
    
    /**
     * get format string "2021-01-16 17:20:21" of timestamp.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static String getTimeFormatOfSecond(long timeStamp) {
        String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timeStamp));
        return format;
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
        TpsRecorder.TpsSlot currentTps = tpsRecorder.createPointIfAbsent(now);
        
        //1.check monitor keys.
        List<TpsRecorder.TpsSlot> passedSlots = new ArrayList<>();
        for (MonitorKey monitorKey : monitorKeys) {
            Set<Map.Entry<MonitorKeyMatcher, TpsRecorder>> allMonitorKeys = monitorKeysRecorder.entrySet();
            for (Map.Entry<MonitorKeyMatcher, TpsRecorder> entry : allMonitorKeys) {
                if (entry.getKey().match(monitorKey.build())) {
                    TpsRecorder tpsRecorderKey = entry.getValue();
                    
                    TpsRecorder.TpsSlot currentKeySlot = tpsRecorderKey.createPointIfAbsent(now);
                    long maxTpsOfIp = tpsRecorderKey.getMaxTps();
                    boolean overLimit = maxTpsOfIp >= 0 && currentKeySlot.tps.longValue() >= maxTpsOfIp;
                    if (overLimit) {
                        Loggers.TPS_CONTROL_DETAIL
                                .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorModel={}", connectionId,
                                        this.getPointName(), entry.getKey(), tpsRecorderKey.getMonitorType());
                        if (tpsRecorderKey.isInterceptMode()) {
                            currentKeySlot.interceptedTps.incrementAndGet();
                            currentTps.interceptedTps.incrementAndGet();
                            return false;
                        }
                    } else {
                        passedSlots.add(currentKeySlot);
                    }
                }
            }
        }
        
        //2.check total tps.
        long maxTps = tpsRecorder.getMaxTps();
        boolean overLimit = maxTps >= 0 && currentTps.tps.longValue() >= maxTps;
        if (overLimit) {
            Loggers.TPS_CONTROL_DETAIL
                    .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorType={}", connectionId,
                            this.getPointName(), "pointRule", tpsRecorder.getMonitorType());
            if (tpsRecorder.isInterceptMode()) {
                currentTps.interceptedTps.incrementAndGet();
                return false;
            }
        }
        
        currentTps.tps.incrementAndGet();
        for (TpsRecorder.TpsSlot passedTpsSlot : passedSlots) {
            passedTpsSlot.tps.incrementAndGet();
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
                    this.tpsRecorder.getMaxTps(), newPointRule.maxTps, this.tpsRecorder.getMonitorType(),
                    newPointRule.monitorType);
            
            this.tpsRecorder.setMaxTps(newPointRule.maxTps);
            this.tpsRecorder.setMonitorType(newPointRule.monitorType);
        }
        
        //3.check rule for ips.
        Map<String, TpsControlRule.Rule> newMonitorKeyRules = newControlRule.getMonitorKeyRule();
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            Loggers.TPS_CONTROL
                    .info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.stopAllMonitorClient();
        } else {
            Map<MonitorKeyMatcher, TpsRecorder> tpsRecordCurrent = this.monitorKeysRecorder;
            
            for (Map.Entry<String, TpsControlRule.Rule> monitorRule : newMonitorKeyRules.entrySet()) {
                if (monitorRule.getValue() == null) {
                    continue;
                }
                
                MonitorKeyMatcher check = MonitorKeyMatcherValidator.parse(monitorRule.getKey());
                if (check == null) {
                    Loggers.TPS_CONTROL.info("Invalid monitor rule, pointName=[{}] ,monitorRule={} ,Ignore this.",
                            this.getPointName(), monitorRule.getKey());
                    continue;
                }
                
                //update rule.
                if (tpsRecordCurrent.containsKey(check)) {
                    TpsRecorder tpsRecorder = tpsRecordCurrent.get(check);
                    Loggers.TPS_CONTROL
                            .info("Update  point  control rule for client ip ,pointName=[{}],monitorKey=[{}],original maxTps={}"
                                            + ", new maxTps={},original monitorType={}, new monitorType={}, ",
                                    this.getPointName(), monitorRule.getKey(), tpsRecorder.getMaxTps(),
                                    monitorRule.getValue().maxTps, tpsRecorder.getMonitorType(),
                                    monitorRule.getValue().monitorType);
                    tpsRecorder.setMaxTps(monitorRule.getValue().maxTps);
                    tpsRecorder.setMonitorType(monitorRule.getValue().monitorType);
                } else {
                    Loggers.TPS_CONTROL
                            .info("Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new maxTps={}, new monitorType={}, ",
                                    this.getPointName(), monitorRule.getKey(), monitorRule.getValue().maxTps,
                                    monitorRule.getValue().monitorType);
                    // add rule
                    TpsRecorder tpsRecorderAdd = new TpsRecorder(startTime, DEFAULT_RECORD_SIZE);
                    tpsRecorderAdd.setMaxTps(monitorRule.getValue().maxTps);
                    tpsRecorderAdd.setMonitorType(monitorRule.getValue().monitorType);
                    tpsRecordCurrent.put(check, tpsRecorderAdd);
                }
                
            }
            
            //delete rule.
            Iterator<Map.Entry<MonitorKeyMatcher, TpsRecorder>> iteratorCurrent = tpsRecordCurrent.entrySet()
                    .iterator();
            while (iteratorCurrent.hasNext()) {
                Map.Entry<MonitorKeyMatcher, TpsRecorder> next1 = iteratorCurrent.next();
                if (!newMonitorKeyRules.containsKey(next1.getKey().toString())) {
                    Loggers.TPS_CONTROL.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]",
                            this.getPointName(), next1.getKey());
                    iteratorCurrent.remove();
                }
            }
            
        }
        
    }
    
}
