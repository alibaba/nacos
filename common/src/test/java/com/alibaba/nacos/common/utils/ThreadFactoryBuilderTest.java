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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * thread factory builder unit test.
 * @author zzq
 * @date 2021/8/3
 */
public class ThreadFactoryBuilderTest {
    
    int priority = 2;
    
    @Test
    public void simpleTest() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .daemon(true)
                .priority(priority)
                .nameFormat("nacos-grpc-executor-%d")
                .build();
        Thread thread1 = threadFactory.newThread(() -> {
        });
        Assert.assertEquals("nacos-grpc-executor-0", thread1.getName());
        Assert.assertEquals(priority, thread1.getPriority());
        Assert.assertTrue(thread1.isDaemon());
        Thread thread2 = threadFactory.newThread(() -> {
        });
        Assert.assertEquals("nacos-grpc-executor-1", thread2.getName());
        Assert.assertEquals(priority, thread2.getPriority());
        Assert.assertTrue(thread2.isDaemon());
    }
    
    @Test
    public void customizeFactoryTest() {
        String threadName = "hello is me!";
        ThreadFactory myFactory = r -> {
            Thread thread = new Thread();
            thread.setName(threadName);
            return thread;
        };
        ThreadFactory factory = new ThreadFactoryBuilder()
                .daemon(true)
                .priority(priority)
                .customizeFactory(myFactory)
                .build();
        Thread thread = factory.newThread(() -> { });
        Assert.assertEquals(threadName, thread.getName());
    }
    
    @Test
    public void uncaughtExceptionHandlerTest() throws Exception {
        AtomicBoolean state = new AtomicBoolean(false);
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .daemon(true)
                .priority(priority)
                .nameFormat("nacos-grpc-executor-%d")
                .uncaughtExceptionHandler((t, e) -> state.set(true))
                .build();
        threadFactory.newThread(() -> {
            throw new NullPointerException("null pointer");
        }).start();
        TimeUnit.SECONDS.sleep(1);
        Assert.assertTrue(state.get());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void propertyPriorityTest1() {
        new ThreadFactoryBuilder()
                .priority(11)
                .nameFormat("nacos-grpc-executor-%d")
                .build();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void propertyPriorityTest2() {
        new ThreadFactoryBuilder()
                .priority(-1)
                .nameFormat("nacos-grpc-executor-%d")
                .build();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void propertyNameFormatTest() {
        new ThreadFactoryBuilder()
                .priority(priority)
                .nameFormat(null)
                .build();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void propertyUncaughtExceptionHandlerTest() {
        new ThreadFactoryBuilder()
                .priority(priority)
                .nameFormat("nacos-grpc-executor-%d")
                .uncaughtExceptionHandler(null)
                .build();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void propertyCustomizeFactoryHandlerTest() {
        new ThreadFactoryBuilder()
                .priority(priority)
                .nameFormat("nacos-grpc-executor-%d")
                .customizeFactory(null)
                .build();
    }
}
