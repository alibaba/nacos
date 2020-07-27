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

import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread utils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ThreadUtils {
    
    /**
     * Wait.
     *
     * @param object load object
     */
    public static void objectWait(Object object) {
        try {
            object.wait();
        } catch (InterruptedException ignore) {
            Thread.interrupted();
        }
    }
    
    /**
     * Sleep.
     *
     * @param millis sleep millisecond
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public static void countDown(CountDownLatch latch) {
        Objects.requireNonNull(latch, "latch");
        latch.countDown();
    }
    
    /**
     * Await count down latch.
     *
     * @param latch count down latch
     */
    public static void latchAwait(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Await count down latch with timeout.
     *
     * @param latch count down latch
     * @param time  timeout time
     * @param unit  time unit
     */
    public static void latchAwait(CountDownLatch latch, long time, TimeUnit unit) {
        try {
            latch.await(time, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Through the number of cores, calculate the appropriate number of threads; 1.5-2 times the number of CPU cores.
     *
     * @return thread count
     */
    public static int getSuitableThreadCount() {
        final int coreCount = Runtime.getRuntime().availableProcessors();
        int workerCount = 1;
        while (workerCount < coreCount * THREAD_MULTIPLER) {
            workerCount <<= 1;
        }
        return workerCount;
    }
    
    public static void shutdownThreadPool(ExecutorService executor) {
        shutdownThreadPool(executor, null);
    }
    
    /**
     * Shutdown thread pool.
     *
     * @param executor thread pool
     * @param logger   logger
     */
    public static void shutdownThreadPool(ExecutorService executor, Logger logger) {
        executor.shutdown();
        int retry = 3;
        while (retry > 0) {
            retry--;
            try {
                if (executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.interrupted();
            } catch (Throwable ex) {
                if (logger != null) {
                    logger.error("ThreadPoolManager shutdown executor has error : {}", ex);
                }
            }
        }
        executor.shutdownNow();
    }
    
    public static void addShutdownHook(Runnable runnable) {
        Runtime.getRuntime().addShutdownHook(new Thread(runnable));
    }
    
    private static final int THREAD_MULTIPLER = 2;
    
}
