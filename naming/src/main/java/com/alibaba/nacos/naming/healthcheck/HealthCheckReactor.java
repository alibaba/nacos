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
package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.naming.misc.Loggers;

import java.util.Map;
import java.util.concurrent.*;

/**
 * @author nacos
 */
public class HealthCheckReactor {

    private static final ScheduledExecutorService EXECUTOR;

    private static Map<String, ScheduledFuture> futureMap = new ConcurrentHashMap<>();

    static {

        int processorCount = Runtime.getRuntime().availableProcessors();
        EXECUTOR
                = Executors
                .newScheduledThreadPool(processorCount <= 1 ? 1 : processorCount / 2, new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true);
                        thread.setName("com.alibaba.nacos.naming.health");
                        return thread;
                    }
                });
    }

    public static ScheduledFuture<?> scheduleCheck(HealthCheckTask task) {
        task.setStartTime(System.currentTimeMillis());
        return EXECUTOR.schedule(task, task.getCheckRTNormalized(), TimeUnit.MILLISECONDS);
    }

    public static void scheduleCheck(ClientBeatCheckTask task) {
        futureMap.putIfAbsent(task.taskKey(), EXECUTOR.scheduleWithFixedDelay(task, 5000, 5000, TimeUnit.MILLISECONDS));
    }

    public static void cancelCheck(ClientBeatCheckTask task) {
        ScheduledFuture scheduledFuture = futureMap.get(task.taskKey());
        if (scheduledFuture == null) {
            return;
        }
        try {
            scheduledFuture.cancel(true);
        } catch (Exception e) {
            Loggers.EVT_LOG.error("[CANCEL-CHECK] cancel failed!", e);
        }
    }


    public static ScheduledFuture<?> scheduleNow(Runnable task) {
        return EXECUTOR.schedule(task, 0, TimeUnit.MILLISECONDS);
    }
}
