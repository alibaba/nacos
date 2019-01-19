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
package com.alibaba.nacos.naming.raft;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author nacos
 */
public class GlobalExecutor {

    public static final long HEARTBEAT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5L);

    public static final long LEADER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15L);

    public static final long RANDOM_MS = TimeUnit.SECONDS.toMillis(5L);

    public static final long TICK_PERIOD_MS = TimeUnit.MILLISECONDS.toMillis(500L);

    public static final long ADDRESS_SERVER_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5L);

    private static ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.raft.timer");

            return t;
        }
    });


    public static void register(Runnable runnable) {
        executorService.scheduleAtFixedRate(runnable, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    public static void register1(Runnable runnable) {
        executorService.scheduleWithFixedDelay(runnable, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    public static void register(Runnable runnable, long delay) {
        executorService.scheduleAtFixedRate(runnable, 0, delay, TimeUnit.MILLISECONDS);
    }
}
