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

import com.alibaba.nacos.naming.healthcheck.heartbeat.BeatCheckTask;
import com.alibaba.nacos.naming.healthcheck.interceptor.HealthCheckTaskInterceptWrapper;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Health check reactor.
 *
 * @author nacos
 */
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class HealthCheckReactor {
    
    private static Map<String, ScheduledFuture> futureMap = new ConcurrentHashMap<>();
    
    /**
     * Schedule health check task.
     *
     * @param task health check task
     * @return scheduled future
     */
    public static ScheduledFuture<?> scheduleCheck(HealthCheckTask task) {
        task.setStartTime(System.currentTimeMillis());
        return GlobalExecutor.scheduleNamingHealth(task, task.getCheckRtNormalized(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Schedule health check task for v2.
     *
     * @param task health check task
     */
    public static void scheduleCheck(HealthCheckTaskV2 task) {
        task.setStartTime(System.currentTimeMillis());
        Runnable wrapperTask = new HealthCheckTaskInterceptWrapper(task);
        GlobalExecutor.scheduleNamingHealth(wrapperTask, task.getCheckRtNormalized(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Schedule client beat check task with a delay.
     *
     * @param task client beat check task
     */
    public static void scheduleCheck(BeatCheckTask task) {
        Runnable wrapperTask =
                task instanceof NacosHealthCheckTask ? new HealthCheckTaskInterceptWrapper((NacosHealthCheckTask) task)
                        : task;
        futureMap.computeIfAbsent(task.taskKey(),
                k -> GlobalExecutor.scheduleNamingHealth(wrapperTask, 5000, 5000, TimeUnit.MILLISECONDS));
    }
    
    /**
     * Cancel client beat check task.
     *
     * @param task client beat check task
     */
    public static void cancelCheck(BeatCheckTask task) {
        ScheduledFuture scheduledFuture = futureMap.get(task.taskKey());
        if (scheduledFuture == null) {
            return;
        }
        try {
            scheduledFuture.cancel(true);
            futureMap.remove(task.taskKey());
        } catch (Exception e) {
            Loggers.EVT_LOG.error("[CANCEL-CHECK] cancel failed!", e);
        }
    }
    
    /**
     * Schedule client beat check task without a delay.
     *
     * @param task health check task
     * @return scheduled future
     */
    public static ScheduledFuture<?> scheduleNow(Runnable task) {
        return GlobalExecutor.scheduleNamingHealth(task, 0, TimeUnit.MILLISECONDS);
    }
}
