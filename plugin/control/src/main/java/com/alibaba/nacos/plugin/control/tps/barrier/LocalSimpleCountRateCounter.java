/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.tps.barrier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * local simple count rate counter.
 *
 * @author shiyiyue
 */
public class LocalSimpleCountRateCounter extends RateCounter {
    
    private static final int DEFAULT_RECORD_SIZE = 10;
    
    long startTime = System.currentTimeMillis();
    
    private List<TpsSlot> slotList;
    
    public LocalSimpleCountRateCounter(String name, TimeUnit period) {
        super(name, period);
        slotList = new ArrayList<>(DEFAULT_RECORD_SIZE);
        for (int i = 0; i < DEFAULT_RECORD_SIZE; i++) {
            slotList.add(new TpsSlot());
        }
        long now = System.currentTimeMillis();
        
        if (period == TimeUnit.SECONDS) {
            startTime = RateCounter.getTrimMillsOfSecond(now);
        } else if (period == TimeUnit.MINUTES) {
            startTime = RateCounter.getTrimMillsOfMinute(now);
        } else if (period == TimeUnit.HOURS) {
            startTime = RateCounter.getTrimMillsOfHour(now);
        } else {
            //second default
            startTime = RateCounter.getTrimMillsOfSecond(now);
        }
    }
    
    @Override
    public long add(long timestamp, long count) {
        return createSlotIfAbsent(timestamp).countHolder.count.addAndGet(count);
    }

    @Override
    public boolean tryAdd(long timestamp, long countDelta, long upperLimit) {
        if (createSlotIfAbsent(timestamp).countHolder.count.addAndGet(countDelta) <= upperLimit) {
            return true;
        } else {
            createSlotIfAbsent(timestamp).countHolder.interceptedCount.addAndGet(countDelta);
            return false;
        }
    }

    public void minus(long timestamp, long count) {
        AtomicLong currentCount = createSlotIfAbsent(timestamp).countHolder.count;
        currentCount.addAndGet(count * -1);
    }
    
    public long getCount(long timestamp) {
        TpsSlot point = getPoint(timestamp);
        return point == null ? 0L : point.countHolder.count.longValue();
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
