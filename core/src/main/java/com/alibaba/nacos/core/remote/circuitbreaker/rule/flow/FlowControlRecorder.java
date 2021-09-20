package com.alibaba.nacos.core.remote.circuitbreaker.rule.flow;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;

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
    }

    @Override
    public CircuitBreakerConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(CircuitBreakerConfig config) {
        this.config = (FlowControlConfig) config;
    }

    @Override
    protected Slot createSlot() { return new FlowControlSlot(); }

    @Override
    protected Slot createMultiKeySlot() { return new MultiKeyFlowControlSlot(); }

    static class FlowControlSlot extends Slot {

        @Override
        public void initCountHolder() {
            this.countHolder = new LoadCountHolder();
        }

        @Override
        public SlotCountHolder getCountHolder(String key) {
            return countHolder;
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
