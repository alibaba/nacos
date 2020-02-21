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
package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.alibaba.nacos.naming.NamingApp;

import java.util.concurrent.*;

/**
 * @author nacos
 */
public class GlobalExecutor {

    private static final long NACOS_SERVER_LIST_REFRESH_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private static final long SERVER_STATUS_UPDATE_PERIOD = TimeUnit.SECONDS.toMillis(5);

    private static ScheduledExecutorService executorService = ExecutorFactory
            .newScheduledExecutorService(
                    NamingApp.class.getCanonicalName(),
                    Runtime.getRuntime().availableProcessors(),
                    new NameThreadFactory("com.alibaba.nacos.naming.timer")
            );

    private static ScheduledExecutorService notifyServerListExecutor =
        ExecutorFactory.newSingleScheduledExecutorService(
                NamingApp.class.getCanonicalName(),
                new NameThreadFactory("com.alibaba.nacos.naming.server.list.notifier"));

    private static final ScheduledExecutorService SERVER_STATUS_EXECUTOR
        = ExecutorFactory.newSingleScheduledExecutorService(
                    NamingApp.class.getCanonicalName(),
                    new NameThreadFactory("nacos.naming.status.worker"));

    /**
     * thread pool that processes getting service detail from other server asynchronously
     */
    private static ExecutorService serviceUpdateExecutor
        = ExecutorFactory.newFixExecutorService(
            NamingApp.class.getCanonicalName(),
            2,
            new NameThreadFactory("com.alibaba.nacos.naming.service.update.http.handler"));

    public static void registerServerListUpdater(Runnable runnable) {
        executorService.scheduleAtFixedRate(runnable, 0, NACOS_SERVER_LIST_REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public static void registerServerStatusUpdater(Runnable runnable) {
        executorService.scheduleAtFixedRate(runnable, 0, SERVER_STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    }

    public static void schedule(Runnable runnable, long period) {
        executorService.scheduleAtFixedRate(runnable, 0, period, TimeUnit.MILLISECONDS);
    }

    public static void schedule(Runnable runnable, long initialDelay, long period) {
        executorService.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    public static void submit(Runnable runnable) {
        executorService.submit(runnable);
    }

    public static void execute(Runnable runnable) {
        executorService.execute(runnable);
    }

    public static void notifyServerListChange(Runnable runnable) {
        notifyServerListExecutor.submit(runnable);
    }

    public static void submitServiceUpdate(Runnable runnable) {
        serviceUpdateExecutor.execute(runnable);
    }

    public static void registerServerStatusReporter(Runnable runnable, long delay) {
        SERVER_STATUS_EXECUTOR.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
}
