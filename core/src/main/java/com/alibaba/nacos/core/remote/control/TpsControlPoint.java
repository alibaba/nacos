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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsControlPoint {
    
    public static final int DEFAULT_RECORD_SIZE = 10;
    
    private long startTime;
    
    private String pointName;
    
    private TpsRecorder tpsRecorder;
    
    Map<String, TpsRecorder> tpsRecordForIp = new HashMap<String, TpsRecorder>();
    
    public TpsControlPoint(String pointName) {
        this(pointName, -1, "monitor");
    }
    
    public TpsControlPoint(String pointName, int maxTps, String monitorType) {
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
        tpsRecordForIp.clear();
    }
    
    /**
     * increase tps.
     *
     * @param clientIp client ip .
     * @return check current tps is allowed.
     */
    public boolean applyTps(String clientIp) {
        
        long now = System.currentTimeMillis();
        TpsRecorder.TpsSlot currentTps = tpsRecorder.createPointIfAbsent(now);
        
        //1.check ip tps.
        TpsRecorder.TpsSlot currentIpTps = null;
        if (tpsRecordForIp.containsKey(clientIp)) {
            TpsRecorder tpsRecorderIp = tpsRecordForIp.get(clientIp);
            
            currentIpTps = tpsRecorderIp.createPointIfAbsent(now);
            long maxTpsOfIp = tpsRecorderIp.getMaxTps();
            boolean overLimit = maxTpsOfIp >= 0 && currentIpTps.tps.longValue() >= maxTpsOfIp;
            if (overLimit) {
                Loggers.TPS_CONTROL_DETAIL
                        .info("tps over limit ,pointName=[{}],clientIp=[{}],barrier=[{}]，monitorType={}",
                                this.getPointName(), clientIp, "ipRule", tpsRecorderIp.getMonitorType());
                if (tpsRecorderIp.isInterceptMode()) {
                    currentIpTps.interceptedTps.incrementAndGet();
                    currentTps.interceptedTps.incrementAndGet();
                    return false;
                }
            }
            
        }
        
        //2.check total tps.
        long maxTps = tpsRecorder.getMaxTps();
        boolean overLimit = maxTps >= 0 && currentTps.tps.longValue() >= maxTps;
        if (overLimit) {
            Loggers.TPS_CONTROL_DETAIL.info("tps over limit ,pointName=[{}],clientIp=[{}],barrier=[{}]，monitorType={}",
                    this.getPointName(), clientIp, "pointRule", tpsRecorder.getMonitorType());
            if (tpsRecorder.isInterceptMode()) {
                currentTps.interceptedTps.incrementAndGet();
                return false;
            }
        }
        
        currentTps.tps.incrementAndGet();
        if (currentIpTps != null) {
            currentIpTps.tps.incrementAndGet();
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
     * @param controlRule controlRule.
     */
    public synchronized void applyRule(TpsControlRule controlRule) {
        
        Loggers.TPS_CONTROL.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (controlRule == null) {
            Loggers.TPS_CONTROL.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            this.tpsRecorder.clearLimitRule();
            this.stopAllMonitorClient();
            return;
        }
        
        //2.check point rule.
        TpsControlRule.Rule pointRule = controlRule.getPointRule();
        if (pointRule == null) {
            Loggers.TPS_CONTROL.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            this.tpsRecorder.clearLimitRule();
        } else {
            Loggers.TPS_CONTROL.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.tpsRecorder.getMaxTps(), pointRule.maxTps, this.tpsRecorder.getMonitorType(),
                    pointRule.monitorType);
            
            this.tpsRecorder.setMaxTps(pointRule.maxTps);
            this.tpsRecorder.setMonitorType(pointRule.monitorType);
        }
        
        //3.check rule for ips.
        Map<String, TpsControlRule.Rule> ipRule = controlRule.getIpRule();
        if (controlRule.getIpRule() == null || ipRule.isEmpty()) {
            Loggers.TPS_CONTROL.info("Clear point  control rule for client ips, pointName=[{}]  ", this.getPointName());
            this.stopAllMonitorClient();
        } else {
            Map<String, TpsRecorder> tpsRecordForIp = this.tpsRecordForIp;
            
            for (Map.Entry<String, TpsControlRule.Rule> clientIpRule : ipRule.entrySet()) {
                if (clientIpRule.getValue() == null) {
                    continue;
                }
                //update rule.
                if (tpsRecordForIp.containsKey(clientIpRule.getKey())) {
                    TpsRecorder tpsRecorder = tpsRecordForIp.get(clientIpRule.getKey());
                    Loggers.TPS_CONTROL
                            .info("Update  point  control rule for client ip ,pointName=[{}],client ip=[{}],original maxTps={}"
                                            + ", new maxTps={},original monitorType={}, new monitorType={}, ",
                                    this.getPointName(), clientIpRule.getKey(), tpsRecorder.getMaxTps(),
                                    clientIpRule.getValue().maxTps, tpsRecorder.getMonitorType(),
                                    clientIpRule.getValue().monitorType);
                    tpsRecorder.setMaxTps(clientIpRule.getValue().maxTps);
                    tpsRecorder.setMonitorType(clientIpRule.getValue().monitorType);
                } else {
                    Loggers.TPS_CONTROL
                            .info("Add  point  control rule for client ip ,pointName=[{}],client ip=[{}], new maxTps={}, new monitorType={}, ",
                                    this.getPointName(), clientIpRule.getKey(), clientIpRule.getValue().maxTps,
                                    clientIpRule.getValue().monitorType);
                    // add rule
                    TpsRecorder tpsRecorderAdd = new TpsRecorder(startTime, DEFAULT_RECORD_SIZE);
                    tpsRecorderAdd.setMaxTps(clientIpRule.getValue().maxTps);
                    tpsRecorderAdd.setMonitorType(clientIpRule.getValue().monitorType);
                    tpsRecordForIp.put(clientIpRule.getKey(), tpsRecorderAdd);
                }
                
            }
            
            //delete rule.
            Iterator<Map.Entry<String, TpsRecorder>> iteratorCurrent = tpsRecordForIp.entrySet().iterator();
            while (iteratorCurrent.hasNext()) {
                Map.Entry<String, TpsRecorder> next1 = iteratorCurrent.next();
                if (!ipRule.containsKey(next1.getKey())) {
                    Loggers.TPS_CONTROL.info("Delete  point  control rule for client ip ,pointName=[{}],client ip=[{}]",
                            this.getPointName(), next1.getKey());
                    iteratorCurrent.remove();
                }
            }
            
        }
        
    }
    
}
