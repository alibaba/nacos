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

package com.alibaba.nacos.core.remote.circuitbreaker;
import com.alibaba.nacos.core.remote.control.TpsControlRule;
import com.alibaba.nacos.core.remote.control.TpsRecorder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Info class in charge of storing and monitoring current server point status (tps / tps window / network flow etc.)
 * Can be extended for custom implementations
 * TODO: design a generic status implementation that contains necessary fields
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月07日 22:50 PM chuzefang Exp $
 */
public abstract class CircuitBreakerRecorder {

    String pointName;

    String recorderName;

    private long startTime;

    protected int slotSize;

    protected List<Slot> slotList;

    public abstract CircuitBreakerConfig getConfig();

    public abstract void setConfig(CircuitBreakerConfig config);

    public boolean isProtoModel() {
        return TpsControlRule.Rule.MODEL_PROTO.equalsIgnoreCase(getConfig().getModel());
    }

    public String getModel() {
        return getConfig().getModel();
    }

    public void setModel(String model) {
        getConfig().setModel(model);
    }

    public CircuitBreakerRecorder(String pointName, long startTime, int recordSize, CircuitBreakerConfig config) {
        TimeUnit period = config.getPeriod();
        this.startTime = startTime;
        if (period.equals(TimeUnit.MINUTES)) {
            this.startTime = CircuitBreakerMonitor.getTrimMillsOfMinute(startTime);
        }
        if (period.equals(TimeUnit.HOURS)) {
            this.startTime = CircuitBreakerMonitor.getTrimMillsOfHour(startTime);
        }
        this.slotSize = recordSize + 1;
        this.setPointName(pointName);
    }

    public Slot getPoint(long timeStamp) {

        TimeUnit period = getConfig().getPeriod();
        long distance = timeStamp - startTime;
        long diff = (distance < 0 ? distance + period.toMillis(1) * slotSize : distance) / period.toMillis(1);
        long currentWindowTime = startTime + diff * period.toMillis(1);
        int index = (int) diff % slotSize;
        Slot tpsSlot = slotList.get(index);
        if (tpsSlot.time != currentWindowTime) {
            return null;
        }
        return tpsSlot;
    }

    public Slot createSlotIfAbsent(long timeStamp) {
        long distance = timeStamp - startTime;

        TimeUnit period = getConfig().getPeriod();
        long diff = (distance < 0 ? distance + period.toMillis(1) * slotSize : distance) / period.toMillis(1);
        long currentWindowTime = startTime + diff * period.toMillis(1);
        int index = (int) diff % slotSize;
        if (slotList.get(index).time != currentWindowTime) {
            slotList.get(index).reset(currentWindowTime);
        }
        return slotList.get(index);
    }

    public static class Slot {
        public long time = 0L;

        public void reset(long second) {
            synchronized (this) {
                if (this.time != second) {
                    this.time = second;
                    getCountHolder("").count.set(0L);
                    countHolder.interceptedCount.set(0);
                }
            }
        }

        public SlotCountHolder countHolder;

        public void initCountHolder() {
            countHolder = new SlotCountHolder();
        }

        public SlotCountHolder getCountHolder(String key) {
            if (countHolder == null) {
                initCountHolder();
            }
            return countHolder;
        }

        @Override
        public String toString() {
            return "Slot{" + "time=" + time + ", countHolder=" + countHolder + '}';
        }
    }

    public static class MultiKeySlot extends Slot {

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
            return "MultiKeySlot{" + "time=" + time + "}'";
        }

    }

    public static class SlotCountHolder {

        public AtomicLong count = new AtomicLong();

        public AtomicLong interceptedCount = new AtomicLong();

        @Override
        public String toString() {
            return "{" + count + "|" + interceptedCount + '}';
        }
    }

    public String getPointName() { return pointName; }

    public void setPointName(String name) { this.pointName = name; }

    public String getRecorderName() { return recorderName; }

    public void setRecorderName(String recorderName) { this.recorderName = recorderName; }

    public List<Slot> getSlotList() {
        return slotList;
    }
}
