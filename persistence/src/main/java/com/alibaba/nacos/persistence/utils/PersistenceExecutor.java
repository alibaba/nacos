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

package com.alibaba.nacos.persistence.utils;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Persistence async task executors.
 *
 * @author xiweng.yy
 */
public class PersistenceExecutor {
    
    private static final ScheduledExecutorService TIMER_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(PersistenceExecutor.class.getCanonicalName(), 2,
                    new NameThreadFactory("com.alibaba.nacos.persistence.timer"));
    
    private static final Executor DUMP_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(PersistenceExecutor.class.getCanonicalName(),
                    new NameThreadFactory("com.alibaba.nacos.persistence.embedded.dump"));
    
    private static final ExecutorService EMBEDDED_SNAPSHOT_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(PersistenceExecutor.class.getCanonicalName(),
                    new NameThreadFactory("com.alibaba.nacos.persistence.embedded.snapshot"));
    
    public static void scheduleTask(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        TIMER_EXECUTOR.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
    
    public static void executeEmbeddedDump(Runnable runnable) {
        DUMP_EXECUTOR.execute(runnable);
    }
    
    public static void executeSnapshot(Runnable runnable) {
        EMBEDDED_SNAPSHOT_EXECUTOR.execute(runnable);
    }
}
