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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * thread factory builder unit test.
 *
 * @author zzq
 * @date 2021/8/3
 */
class ThreadFactoryBuilderTest {
    
    int priority = 2;
    
    @Test
    void simpleTest() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder().daemon(true).priority(priority)
                .nameFormat("nacos-grpc-executor-%d").build();
        Thread thread1 = threadFactory.newThread(() -> {
        });
        assertEquals("nacos-grpc-executor-0", thread1.getName());
        assertEquals(priority, thread1.getPriority());
        assertTrue(thread1.isDaemon());
        Thread thread2 = threadFactory.newThread(() -> {
        });
        assertEquals("nacos-grpc-executor-1", thread2.getName());
        assertEquals(priority, thread2.getPriority());
        assertTrue(thread2.isDaemon());
    }
    
    @Test
    void customizeFactoryTest() {
        String threadName = "hello is me!";
        ThreadFactory myFactory = r -> {
            Thread thread = new Thread();
            thread.setName(threadName);
            return thread;
        };
        ThreadFactory factory = new ThreadFactoryBuilder().daemon(true).priority(priority).customizeFactory(myFactory).build();
        Thread thread = factory.newThread(() -> {
        });
        assertEquals(threadName, thread.getName());
    }
    
    @Test
    void uncaughtExceptionHandlerTest() throws Exception {
        AtomicBoolean state = new AtomicBoolean(false);
        ThreadFactory threadFactory = new ThreadFactoryBuilder().daemon(true).priority(priority)
                .nameFormat("nacos-grpc-executor-%d").uncaughtExceptionHandler((t, e) -> state.set(true)).build();
        threadFactory.newThread(() -> {
            throw new NullPointerException("null pointer");
        }).start();
        TimeUnit.SECONDS.sleep(1);
        assertTrue(state.get());
    }
    
    @Test
    void propertyPriorityTest1() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadFactoryBuilder().priority(11).nameFormat("nacos-grpc-executor-%d").build();
        });
    }
    
    @Test
    void propertyPriorityTest2() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadFactoryBuilder().priority(-1).nameFormat("nacos-grpc-executor-%d").build();
        });
    }
    
    @Test
    void propertyNameFormatTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadFactoryBuilder().priority(priority).nameFormat(null).build();
        });
    }
    
    @Test
    void propertyUncaughtExceptionHandlerTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadFactoryBuilder().priority(priority).nameFormat("nacos-grpc-executor-%d").uncaughtExceptionHandler(null)
                    .build();
        });
    }
    
    @Test
    void propertyCustomizeFactoryHandlerTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadFactoryBuilder().priority(priority).nameFormat("nacos-grpc-executor-%d").customizeFactory(null).build();
        });
    }
}
