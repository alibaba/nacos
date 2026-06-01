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

package com.alibaba.nacos.core.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlobalExecutor tests: pass Runnables with a "tag" (e.g. AtomicBoolean) and verify the tag
 * is modified to confirm the runnable was executed. No EnvUtil mock.
 * Note: GlobalExecutor static init uses EnvUtil; these tests pass when run in the full core
 * test suite where the environment is already initialized.
 */
class GlobalExecutorTest {
    
    @Test
    void runWithoutThreadRunsInCallerThreadAndTagIsSet() {
        AtomicBoolean tag = new AtomicBoolean(false);
        GlobalExecutor.runWithoutThread(() -> tag.set(true));
        assertTrue(tag.get());
    }
    
    @Test
    void executeByCommonRunsTaskAndTagIsSet() throws InterruptedException {
        AtomicBoolean tag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GlobalExecutor.executeByCommon(() -> {
            tag.set(true);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(tag.get());
    }
    
    @Test
    void scheduleByCommonRunsTaskAndTagIsSet() throws InterruptedException {
        AtomicBoolean tag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GlobalExecutor.scheduleByCommon(() -> {
            tag.set(true);
            latch.countDown();
        }, 0);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(tag.get());
    }
    
    @Test
    void scheduleWithFixDelayByCommonRunsTaskAndTagIsSet() throws InterruptedException {
        AtomicBoolean tag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> {
            tag.set(true);
            latch.countDown();
        }, 10);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(tag.get());
    }
    
    @Test
    void submitLoadDataTaskRunsTaskAndTagIsSet() throws InterruptedException {
        AtomicBoolean tag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GlobalExecutor.submitLoadDataTask(() -> {
            tag.set(true);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(tag.get());
    }
    
    @Test
    void submitLoadDataTaskWithDelayRunsTaskAndTagIsSet() throws InterruptedException {
        AtomicBoolean tag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GlobalExecutor.submitLoadDataTask(() -> {
            tag.set(true);
            latch.countDown();
        }, 0);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(tag.get());
    }
    
    @Test
    void schedulePartitionDataTimedSyncRunsTaskAndTagIsSet() throws InterruptedException {
        AtomicBoolean tag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GlobalExecutor.schedulePartitionDataTimedSync(() -> {
            tag.set(true);
            latch.countDown();
        }, 50);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(tag.get());
    }
}
