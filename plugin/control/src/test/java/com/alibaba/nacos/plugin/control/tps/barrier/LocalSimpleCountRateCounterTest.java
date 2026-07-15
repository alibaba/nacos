/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSimpleCountRateCounterTest {
    
    @Test
    void testAddTryAddMinusAndWindowLookup() {
        LocalSimpleCountRateCounter counter =
            new LocalSimpleCountRateCounter("point", TimeUnit.SECONDS);
        long timestamp = counter.startTime;
        
        assertEquals(2L, counter.add(timestamp, 2L));
        assertTrue(counter.tryAdd(timestamp, 1L, 3L));
        assertFalse(counter.tryAdd(timestamp, 1L, 3L));
        assertEquals(4L, counter.getCount(timestamp));
        
        counter.minus(timestamp, 2L);
        assertEquals(2L, counter.getCount(timestamp));
        assertEquals(0L, counter.getCount(timestamp + TimeUnit.SECONDS.toMillis(20)));
        assertTrue(counter.createSlotIfAbsent(timestamp).toString().contains("TpsSlot{time="));
    }
    
    @Test
    void testConstructorTrimsDifferentPeriods() {
        assertEquals(0L, new LocalSimpleCountRateCounter("second", TimeUnit.SECONDS).startTime
            % TimeUnit.SECONDS.toMillis(1));
        assertEquals(0L, new LocalSimpleCountRateCounter("minute", TimeUnit.MINUTES).startTime
            % TimeUnit.MINUTES.toMillis(1));
        assertEquals(0L, new LocalSimpleCountRateCounter("hour", TimeUnit.HOURS).startTime
            % TimeUnit.HOURS.toMillis(1));
        assertEquals(0L, new LocalSimpleCountRateCounter("default", TimeUnit.DAYS).startTime
            % TimeUnit.SECONDS.toMillis(1));
    }
}
