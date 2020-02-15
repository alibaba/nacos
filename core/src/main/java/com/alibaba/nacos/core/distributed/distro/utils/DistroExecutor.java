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
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DistroExecutor {

    private static final ExecutorService DISTRO_GLOBAL = ExecutorFactory.newFixExecutorService(
            DistroProtocol.class.getCanonicalName(),
            8,
            new NameThreadFactory("com.alibaba.nacos.core.protocol.distro.common"));

    private static final ScheduledExecutorService DISTRO_TASK_WORKER = ExecutorFactory.newScheduledExecutorService(
            DistroProtocol.class.getCanonicalName(),
            Runtime.getRuntime().availableProcessors(),
            new NameThreadFactory("com.alibaba.nacos.core.protocol.distro.task.worker"));

    public static void executeByGlobal(Runnable runnable) {
        DISTRO_GLOBAL.execute(runnable);
    }

    public static void executeWorker(Runnable runnable) {
        DISTRO_TASK_WORKER.submit(runnable);
    }
}
