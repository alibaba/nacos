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

package com.alibaba.nacos.core.distributed.distro.utils;

import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.core.DataSyncer;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DistroExecutor {

    private static final long PARTITION_DATA_TIMED_SYNC_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private static final ExecutorService DISTRO_GLOBAL = ExecutorFactory.newForkJoinPool(
            DistroProtocol.class.getCanonicalName(),
            8);

    private static final ScheduledExecutorService DISTRO_TASK_WORKER = ExecutorFactory.newScheduledExecutorService(
            DistroProtocol.class.getCanonicalName(),
            Runtime.getRuntime().availableProcessors(),
            new NameThreadFactory("com.alibaba.nacos.core.protocol.distro.task.worker"));

    private static final ScheduledExecutorService DATA_SYNC_EXECUTOR = ExecutorFactory.newScheduledExecutorService(
            DataSyncer.class.getCanonicalName(),
            Runtime.getRuntime().availableProcessors(),
            new NameThreadFactory("com.alibaba.nacos.naming.distro.data.syncer"));

    public static void executeByGlobal(Runnable runnable) {
        DISTRO_GLOBAL.execute(runnable);
    }

    public static void executeWorker(Runnable runnable) {
        DISTRO_TASK_WORKER.submit(runnable);
    }

    public static void scheduleDataSync(Runnable runnable, long delay, TimeUnit unit) {
        DATA_SYNC_EXECUTOR.schedule(runnable, delay, unit);
    }

    public static void schedulePartitionDataTimedSync(Runnable runnable) {
        DISTRO_TASK_WORKER.scheduleWithFixedDelay(runnable, PARTITION_DATA_TIMED_SYNC_INTERVAL,
                PARTITION_DATA_TIMED_SYNC_INTERVAL, TimeUnit.MILLISECONDS);
    }
}
