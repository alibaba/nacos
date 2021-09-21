/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.circuitbreaker.rule.flow;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FlowControlRecorder extends CircuitBreakerRecorder {

    private FlowControlConfig config;

    /**
     * monitor/intercept.
     */
    public FlowControlRecorder(String pointnName, long startTime, int recordSize, FlowControlConfig config) {
        super(pointnName, startTime, recordSize, config);
        this.config = config;
        slotList = new ArrayList<>(slotSize);
        for (int i = 0; i < slotSize; i++) {
            slotList.add(isProtoModel() ? new MultiKeyFlowControlSlot() : new FlowControlSlot());
        }
    }

    @Override
    public CircuitBreakerConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(CircuitBreakerConfig config) {
        this.config = (FlowControlConfig) config;
    }

    static class FlowControlSlot extends Slot {

        @Override
        public void initCountHolder() {
            this.countHolder = new LoadCountHolder();
        }

        @Override
        public void reset(long second) {
            synchronized (this) {
                if (this.time != second) {
                    this.time = second;
                    getCountHolder("").count.set(0L);
                    countHolder.interceptedCount.set(0);
                    ((LoadCountHolder)countHolder).interceptedLoad.set(0);
                    ((LoadCountHolder)countHolder).load.set(0);
                }
            }
        }

        @Override
        public LoadCountHolder getCountHolder(String key) {
            if (countHolder == null) {
                initCountHolder();
            }
            return (LoadCountHolder) countHolder;
        }

        @Override
        public String toString() {
            return "FlowControlSlot{" + "time=" + time + ", countHolder=" + countHolder + '}';
        }

    }

    static class MultiKeyFlowControlSlot extends FlowControlRecorder.FlowControlSlot {

        Map<String, LoadCountHolder> keySlots = new HashMap<>(16);

        @Override
        public LoadCountHolder getCountHolder(String key) {
            if (!keySlots.containsKey(key)) {
                keySlots.putIfAbsent(key, new LoadCountHolder());
            }
            return keySlots.get(key);
        }

        public Map<String, LoadCountHolder> getKeySlots() {
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
            return "MultiKeyFlowControlSlot{" + "time=" + time + "}'";
        }

    }

    public static class LoadCountHolder extends SlotCountHolder {

        public AtomicLong load = new AtomicLong();

        public AtomicLong interceptedLoad = new AtomicLong();
    }
}
