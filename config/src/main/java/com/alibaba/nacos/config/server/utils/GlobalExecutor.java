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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class GlobalExecutor {

    private static ScheduledExecutorService TIME_EXECUTOR = ExecutorFactory
            .newScheduledExecutorService(Config.class.getCanonicalName(), 10,
            new NameThreadFactory("com.alibaba.nacos.server.Timer-"));

    private static final Executor MERGE_EXECUTOR = ExecutorFactory.newFixExecutorService(
            "com.alibaba.nacos.config.server.service.dump.MergeAllDataWorker",
            8,
            new NameThreadFactory("com.alibaba.nacos.config.config-merge")
    );

    private static final ScheduledExecutorService CAPACITY_EXECUTOR = ExecutorFactory
            .newSingleScheduledExecutorService(Config.class.getCanonicalName(),
                    new NameThreadFactory("com.alibaba.nacos.CapacityManagement-"));

    public static void executeByCommon(Runnable runnable) {
        TIME_EXECUTOR.execute(runnable);
    }

    public static void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                              TimeUnit unit) {
        TIME_EXECUTOR.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    public static void executeOnMerge(Runnable runnable) {
        MERGE_EXECUTOR.execute(runnable);
    }

    public static void scheduleCapacityJob(Runnable command, long initialDelay, long delay,
            TimeUnit unit) {
        CAPACITY_EXECUTOR.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

}
