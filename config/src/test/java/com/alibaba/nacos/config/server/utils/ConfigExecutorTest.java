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

package com.alibaba.nacos.config.server.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigExecutorTest {
    
    @Test
    void testScheduleConfigTask() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.scheduleConfigTask(runnable, 0, 10, TimeUnit.MILLISECONDS);
        
        TimeUnit.MILLISECONDS.sleep(10);
        
        assertTrue(atomicInteger.get() >= 1);
        
    }
    
    @Test
    void testScheduleCorrectUsageTask() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.scheduleCorrectUsageTask(runnable, 0, 10, TimeUnit.MILLISECONDS);
        
        TimeUnit.MILLISECONDS.sleep(10);
        
        assertTrue(atomicInteger.get() >= 1);
        
    }
    
    @Test
    void testExecuteAsyncNotify() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.executeAsyncNotify(runnable);
        
        TimeUnit.MILLISECONDS.sleep(20);
        
        assertEquals(1, atomicInteger.get());
        
    }
    
    @Test
    void testScheduleAsyncNotify() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.scheduleAsyncNotify(runnable, 20, TimeUnit.MILLISECONDS);
        
        assertEquals(0, atomicInteger.get());
        
        TimeUnit.MILLISECONDS.sleep(40);
        
        assertEquals(1, atomicInteger.get());
    }
    
    @Test
    void testScheduleLongPollingV1() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.scheduleLongPolling(runnable, 0, 10, TimeUnit.MILLISECONDS);
        
        TimeUnit.MILLISECONDS.sleep(10);
        
        assertTrue(atomicInteger.get() >= 1);
    }
    
    @Test
    void testScheduleLongPollingV2() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.scheduleLongPolling(runnable, 20, TimeUnit.MILLISECONDS);
        
        assertEquals(0, atomicInteger.get());
        
        TimeUnit.MILLISECONDS.sleep(40);
        
        assertEquals(1, atomicInteger.get());
    }
    
    @Test
    void testExecuteLongPolling() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        ConfigExecutor.executeLongPolling(runnable);
        
        TimeUnit.MILLISECONDS.sleep(20);
        
        assertEquals(1, atomicInteger.get());
    }
}
