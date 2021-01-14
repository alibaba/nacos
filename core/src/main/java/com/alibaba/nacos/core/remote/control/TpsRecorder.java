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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * tps record.
 *
 * @author liuzunfei
 * @version $Id: TpsRecorder.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsRecorder {
    
    private long startTime;
    
    private int slotSize;
    
    private List<TpsSlot> slotList;
    
    public TpsRecorder(long startTime, int recordSize) {
        this.startTime = startTime;
        this.slotSize = recordSize + 1;
        slotList = new ArrayList<>(slotSize);
        for (int i = 0; i < slotSize; i++) {
            slotList.add(new TpsSlot());
        }
    }
    
    /**
     * get slot of the timestamp second,create if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return
     */
    public TpsSlot createPointIfAbsent(long timeStamp) {
        long distance = timeStamp - startTime;
        long secondDiff = distance / 1000;
        long currentWindowTime = startTime + secondDiff * 1000;
        int index = (int) secondDiff % slotSize;
        if (slotList.get(index).second != currentWindowTime) {
            slotList.get(index).reset(currentWindowTime);
        }
        return slotList.get(index);
    }
    
    /**
     * get slot of the timestamp second,read only ,return nul if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return
     */
    public TpsSlot getPoint(long timeStamp) {
        long distance = timeStamp - startTime;
        long secondDiff = distance / 1000;
        long currentWindowTime = startTime + secondDiff * 1000;
        int index = (int) secondDiff % slotSize;
        TpsSlot tpsSlot = slotList.get(index);
        if (tpsSlot.second != currentWindowTime) {
            return null;
        }
        return tpsSlot;
    }
    
    private long maxTps = -1;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
    public long getMaxTps() {
        return maxTps;
    }
    
    public void setMaxTps(long maxTps) {
        this.maxTps = maxTps;
    }
    
    public boolean isInterceptMode() {
        return MonitorType.INTERCEPT.type.equals(this.monitorType);
    }
    
    /**
     * clearLimitRule.
     */
    public void clearLimitRule() {
        this.setMonitorType(MonitorType.MONITOR.type);
        this.setMaxTps(-1);
    }
    
    public String getMonitorType() {
        return monitorType;
    }
    
    public void setMonitorType(String monitorType) {
        this.monitorType = monitorType;
    }
    
    static class TpsSlot {
        
        long second = 0L;
        
        AtomicLong tps = new AtomicLong();
        
        public AtomicLong reset(long second) {
            synchronized (this) {
                if (this.second != second) {
                    this.second = second;
                    tps.set(0L);
                }
            }
            return tps;
            
        }
        
        @Override
        public String toString() {
            return "TpsSlot{" + "second=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(Long.valueOf(second))) + ", tps=" + tps + '}';
        }
        
    }
    
    public List<TpsSlot> getSlotList() {
        return slotList;
    }
}
