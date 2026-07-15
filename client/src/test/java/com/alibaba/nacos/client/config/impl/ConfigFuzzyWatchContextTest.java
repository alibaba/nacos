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

package com.alibaba.nacos.client.config.impl;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link ConfigFuzzyWatchContext}, focused on the wait/notify contract of the future
 * returned by {@link ConfigFuzzyWatchContext#createNewFuture()}.
 */
class ConfigFuzzyWatchContextTest {
    
    /**
     * {@code Future#get()} must keep waiting until initialization actually completes: a spurious
     * wake-up (or any {@code notifyAll} that is not paired with {@code initializationCompleted ==
     * true}) must not cause it to return the still-incomplete result set.
     *
     * <p>With the buggy {@code if (...) { wait(); }} form, the spurious {@code notifyAll} below
     * makes {@code get()} return early; with the correct {@code while (...) { wait(); }} form it
     * re-checks the flag and keeps waiting.
     */
    @Test
    void testGetKeepsWaitingOnSpuriousWakeup() throws Exception {
        ConfigFuzzyWatchContext context = new ConfigFuzzyWatchContext("test", "test:pattern");
        Future<Set<String>> future = context.createNewFuture();
        
        AtomicBoolean returned = new AtomicBoolean(false);
        AtomicReference<Set<String>> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread getter = new Thread(() -> {
            try {
                result.set(future.get());
                returned.set(true);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        getter.setDaemon(true);
        getter.start();
        
        // Wait until the getter thread is actually parked inside Object#wait().
        awaitState(getter, Thread.State.WAITING);
        
        // Simulate a spurious wake-up: notify without marking initialization complete.
        synchronized (context) {
            context.notifyAll();
        }
        // Give the getter a chance to (wrongly) wake up and return.
        Thread.sleep(200L);
        
        assertFalse(returned.get(),
            "get() must not return before initialization completes");
        assertTrue(getter.isAlive(), "getter thread must still be waiting");
        assertNotNull(context); // keep the context strongly referenced
        
        // A real completion must release the waiter and yield the received keys.
        context.markInitializationComplete();
        getter.join(2000L);
        
        assertFalse(getter.isAlive(), "getter thread must finish after completion");
        assertTrue(returned.get(), "get() must return after markInitializationComplete");
        assertNull(error.get(), "get() must not raise");
        assertNotNull(result.get(), "completed get() must return a non-null set");
        assertEquals(context.getReceivedGroupKeys(), result.get());
    }
    
    /**
     * When initialization has already completed, {@code Future#get()} must return immediately
     * without blocking.
     */
    @Test
    void testGetReturnsImmediatelyWhenAlreadyCompleted() throws Exception {
        ConfigFuzzyWatchContext context = new ConfigFuzzyWatchContext("test", "test:pattern");
        context.markInitializationComplete();
        Future<Set<String>> future = context.createNewFuture();
        
        Set<String> result = future.get();
        
        assertNotNull(result);
        assertEquals(context.getReceivedGroupKeys(), result);
    }
    
    private static void awaitState(Thread thread, Thread.State expected)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000L;
        while (thread.getState() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(expected, thread.getState(),
            "thread did not reach state " + expected + " in time");
    }
}
