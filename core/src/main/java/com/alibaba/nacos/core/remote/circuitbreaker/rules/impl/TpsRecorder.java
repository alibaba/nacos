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

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;
import com.alibaba.nacos.core.remote.control.TpsControlRule;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TpsRecorder extends CircuitBreakerRecorder {

    private long startTime;

    private int slotSize;

    private List<TpsSlot> slotList;

    private TpsConfig config;

    /**
     * monitor/intercept.
     */
    public TpsRecorder(long startTime, int recordSize, TpsConfig config) {
        TimeUnit period = config.getPeriod();
        this.startTime = startTime;
        if (period.equals(TimeUnit.MINUTES)) {
            this.startTime = TpsMonitorPoint.getTrimMillsOfMinute(startTime);
        }
        if (period.equals(TimeUnit.HOURS)) {
            this.startTime = TpsMonitorPoint.getTrimMillsOfHour(startTime);
        }
        this.slotSize = recordSize + 1;
        this.config = config;
        slotList = new ArrayList<>(slotSize);
        for (int i = 0; i < slotSize; i++) {
            slotList.add(isProtoModel() ? new MultiKeyTpsSlot() : new TpsSlot());
        }
    }

    public boolean isProtoModel() {
        return TpsControlRule.Rule.MODEL_PROTO.equalsIgnoreCase(config.getModel());
    }

    public String getModel() {
        return config.getModel();
    }

    public void setModel(String model) {
        config.setModel(model);
    }

    @Override
    public TpsConfig getConfig() { return config; }

    public void setConfig(TpsConfig config) { this.config = config; }

    /**
     * get slot of the timestamp second,create if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return tps slot.
     */
    public TpsSlot createSlotIfAbsent(long timeStamp) {
        long distance = timeStamp - startTime;

        TimeUnit period = config.getPeriod();
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
    @Override
    public Slot getPoint(long timeStamp) {

        TimeUnit period = config.getPeriod();
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

    static class TpsSlot extends Slot {

        @Override
        public SlotCountHolder getCountHolder(String key) {
            return countHolder;
        }

        public void reset(long second) {
            synchronized (this) {
                if (this.time != second) {
                    this.time = second;
                    getCountHolder("").count.set(0L);
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

    public List<TpsSlot> getSlotList() {
        return slotList;
    }
}

