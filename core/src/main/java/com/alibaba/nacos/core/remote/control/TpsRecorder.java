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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * tps record.
 *
 * @author liuzunfei
 * @version $Id: TpsRecorder.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsRecorder {
    
    private long startTime;
    
    TimeUnit period;
    
    private int slotSize;
    
    private List<TpsSlot> slotList;
    
    private long maxCount = -1;
    
    private String model;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
    public TpsRecorder(long startTime, TimeUnit period, String model, int recordSize) {
        
        this.startTime = startTime;
        if (period.equals(TimeUnit.MINUTES)) {
            this.startTime = TpsMonitorPoint.getTrimMillsOfMinute(startTime);
        }
        if (period.equals(TimeUnit.HOURS)) {
            this.startTime = TpsMonitorPoint.getTrimMillsOfHour(startTime);
        }
        this.period = period;
        this.model = model;
        this.slotSize = recordSize + 1;
        slotList = new ArrayList<>(slotSize);
        for (int i = 0; i < slotSize; i++) {
            slotList.add(isProtoModel() ? new MultiKeyTpsSlot() : new TpsSlot());
        }
    }
    
    public boolean isProtoModel() {
        return TpsControlRule.Rule.MODEL_PROTO.equalsIgnoreCase(this.model);
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * get slot of the timestamp second,create if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return tps slot.
     */
    public TpsSlot createSlotIfAbsent(long timeStamp) {
        long distance = timeStamp - startTime;
        
        long diff = (distance < 0 ? distance + period.toMillis(1) * slotSize : distance) / period.toMillis(1);
        long currentWindowTime = startTime + diff * period.toMillis(1);
        int index = (int) diff % slotSize;
        if (slotList.get(index).time != currentWindowTime) {
            slotList.get(index).reset(currentWindowTime);
        }
        return slotList.get(index);
    }
    
    /**
     * get slot of the timestamp second,read only ,return nul if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return tps slot.
     */
    public TpsSlot getPoint(long timeStamp) {
        long distance = timeStamp - startTime;
        long diff = (distance < 0 ? distance + period.toMillis(1) * slotSize : distance) / period.toMillis(1);
        long currentWindowTime = startTime + diff * period.toMillis(1);
        int index = (int) diff % slotSize;
        TpsSlot tpsSlot = slotList.get(index);
        if (tpsSlot.time != currentWindowTime) {
            return null;
        }
        return tpsSlot;
    }
    
    public long getMaxCount() {
        return maxCount;
    }
    
    public void setMaxCount(long maxCount) {
        this.maxCount = maxCount;
    }
    
    public boolean isInterceptMode() {
        return MonitorType.INTERCEPT.type.equals(this.monitorType);
    }
    
    /**
     * clearLimitRule.
     */
    public void clearLimitRule() {
        this.setMonitorType(MonitorType.MONITOR.type);
        this.setMaxCount(-1);
    }
    
    public String getMonitorType() {
        return monitorType;
    }
    
    public void setMonitorType(String monitorType) {
        this.monitorType = monitorType;
    }
    
    static class TpsSlot {
        
        long time = 0L;
        
        private SlotCountHolder countHolder = new SlotCountHolder();
        
        public SlotCountHolder getCountHolder(String key) {
            return countHolder;
        }
        
        public void reset(long second) {
            synchronized (this) {
                if (this.time != second) {
                    this.time = second;
                    countHolder.count.set(0L);
                    countHolder.interceptedCount.set(0);
                }
            }
        }
        
        @Override
        public String toString() {
            return "TpsSlot{" + "time=" + time + ", countHolder=" + countHolder + '}';
        }
        
    }
    
    static class MultiKeyTpsSlot extends TpsSlot {
        
        Map<String, SlotCountHolder> keySlots = new HashMap<>(16);
        
        @Override
        public SlotCountHolder getCountHolder(String key) {
            if (!keySlots.containsKey(key)) {
                keySlots.putIfAbsent(key, new SlotCountHolder());
            }
            return keySlots.get(key);
        }
        
        public Map<String, SlotCountHolder> getKeySlots() {
            return keySlots;
        }
        
        @Override
        public void reset(long second) {
            synchronized (this) {
                if (this.time != second) {
                    this.time = second;
                    keySlots.clear();
                }
            }
        }
        
        @Override
        public String toString() {
            return "MultiKeyTpsSlot{" + "time=" + time + "}'";
        }
        
    }
    
    static class SlotCountHolder {
        
        AtomicLong count = new AtomicLong();
        
        AtomicLong interceptedCount = new AtomicLong();
        
        @Override
        public String toString() {
            return "{" + count + "|" + interceptedCount + '}';
        }
    }
    
    public List<TpsSlot> getSlotList() {
        return slotList;
    }
}
