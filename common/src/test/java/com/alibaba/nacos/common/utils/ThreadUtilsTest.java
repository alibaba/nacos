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

package com.alibaba.nacos.common.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ThreadUtilsTest {
    
    private ExecutorService executorService;
    
    @Before
    public void setUp() throws Exception {
        System.setProperty("nacos.common.processors", "2");
        executorService = Executors.newFixedThreadPool(1);
    }
    
    @After
    public void tearDown() throws Exception {
        System.setProperty("nacos.common.processors", "");
        ThreadUtils.shutdownThreadPool(executorService);
    }
    
    @Test
    public void testLatchAwait() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        long currentTime = System.currentTimeMillis();
        executorService.execute(() -> {
            ThreadUtils.sleep(100);
            ThreadUtils.countDown(countDownLatch);
        });
        ThreadUtils.latchAwait(countDownLatch);
        assertTrue(System.currentTimeMillis() - currentTime >= 100);
    }
    
    @Test
    public void testLatchAwaitForTimeout() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        long currentTime = System.currentTimeMillis();
        ThreadUtils.latchAwait(countDownLatch, 50, TimeUnit.MILLISECONDS);
        assertTrue(System.currentTimeMillis() - currentTime >= 50);
        
    }
    
    @Test
    public void testGetSuitableThreadCount() {
        assertEquals(4, ThreadUtils.getSuitableThreadCount());
        assertEquals(8, ThreadUtils.getSuitableThreadCount(3));
    }
    
    @Test
    public void testShutdownThreadPoolWithInterruptedException() throws InterruptedException {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(100, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());
        ThreadUtils.shutdownThreadPool(executor);
        verify(executor, times(4)).shutdownNow();
    }
    
    @Test
    public void testShutdownThreadPoolWithOtherException() throws InterruptedException {
        ExecutorService executor = mock(ExecutorService.class);
        Logger logger = mock(Logger.class);
        Throwable cause = new RuntimeException();
        when(executor.awaitTermination(100, TimeUnit.MILLISECONDS)).thenThrow(cause);
        ThreadUtils.shutdownThreadPool(executor, logger);
        verify(executor).shutdownNow();
        verify(logger, times(3)).error("ThreadPoolManager shutdown executor has error : ", cause);
    }
    
    @Test
    public void testAddShutdownHook() {
        Runnable shutdownHook = () -> {
        };
        ThreadUtils.addShutdownHook(shutdownHook);
        // It seems no way to check it.
    }
}