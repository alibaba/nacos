package com.alibaba.nacos.plugin.control.tps.nacos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LocalSimpleCountRateCounter extends RateCounter {
    
    private static final int DEFAULT_RECORD_SIZE = 10;
    
    long startTime = System.currentTimeMillis();
    
    private List<TpsSlot> slotList;
    
    public LocalSimpleCountRateCounter(String name,TimeUnit period) {
        super(name,period);
        slotList = new ArrayList<>(DEFAULT_RECORD_SIZE);
        for (int i = 0; i < DEFAULT_RECORD_SIZE; i++) {
            slotList.add(new TpsSlot());
        }
    }
    
    public void add(long timestamp, long count) {
        createSlotIfAbsent(timestamp).countHolder.count.addAndGet(count);
    }
    
    public boolean tryAdd(long timestamp, long count, long upLimit) {
        AtomicLong currentCount = createSlotIfAbsent(timestamp).countHolder.count;
        if (currentCount.longValue() + count > upLimit) {
            return false;
        } else {
            currentCount.addAndGet(count);
            return true;
        }
    }
    
    public void minus(long timestamp, long count) {
        AtomicLong currentCount = createSlotIfAbsent(timestamp).countHolder.count;
        currentCount.addAndGet(count * -1);
    }
    
    public long getCount(long timestamp) {
        TpsSlot point = getPoint(timestamp);
        
        return point == null ? 0l : point.countHolder.count.longValue();
    }
    
    /**
     * get slot of the timestamp second,read only ,return nul if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return tps slot.
     */
    private TpsSlot getPoint(long timeStamp) {
        long distance = timeStamp - startTime;
        long diff = (distance < 0 ? distance + getPeriod().toMillis(1) * DEFAULT_RECORD_SIZE : distance) / getPeriod()
                .toMillis(1);
        long currentWindowTime = startTime + diff * getPeriod().toMillis(1);
        int index = (int) diff % DEFAULT_RECORD_SIZE;
        TpsSlot tpsSlot = slotList.get(index);
        if (tpsSlot.time != currentWindowTime) {
            return null;
        }
        return tpsSlot;
    }
    
    /**
     * get slot of the timestamp second,create if not exist.
     *
     * @param timeStamp the timestamp second.
     * @return tps slot.
     */
    public TpsSlot createSlotIfAbsent(long timeStamp) {
        long distance = timeStamp - startTime;
        
        long diff = (distance < 0 ? distance + getPeriod().toMillis(1) * DEFAULT_RECORD_SIZE : distance) / getPeriod()
                .toMillis(1);
        long currentWindowTime = startTime + diff * getPeriod().toMillis(1);
        int index = (int) diff % DEFAULT_RECORD_SIZE;
        TpsSlot tpsSlot = slotList.get(index);
        if (tpsSlot.time != currentWindowTime) {
            tpsSlot.reset(currentWindowTime);
        }
        return slotList.get(index);
    }
    
    static class TpsSlot {
        
        long time = 0L;
        
        private SlotCountHolder countHolder = new SlotCountHolder();
        
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
    
    static class SlotCountHolder {
        
        AtomicLong count = new AtomicLong();
        
        AtomicLong interceptedCount = new AtomicLong();
        
        @Override
        public String toString() {
            return "{" + count + "|" + interceptedCount + '}';
        }
    }
}
