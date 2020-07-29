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
package com.alibaba.nacos.naming.remote.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remoting workers manager.
 *
 * @author xiweng.yy
 */
public final class RemotingWorkersManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RemotingWorkersManager.class);

    private static final int TIMES_FOR_CORE = 2;
    
    /**
     * power of 2.
     */
    private static final RemotingWorker[] REMOTING_WORKERS;

    private RemotingWorkersManager() {
    }
    
    static {
        // Find a power of 2 >= cpuCores * 2.
        final int coreCount = Runtime.getRuntime().availableProcessors();
        int workerCount = 1;
        while (workerCount < coreCount * TIMES_FOR_CORE) {
            workerCount <<= 1;
        }
        REMOTING_WORKERS = new RemotingWorker[workerCount];
        for (int mod = 0; mod < workerCount; ++mod) {
            REMOTING_WORKERS[mod] = new RemotingWorker(mod, workerCount);
        }
    }

    /**
     * Dispatch task by connectionId.
     */
    public static void dispatch(String connectionId, Runnable task) {
        RemotingWorker worker = getWorker(connectionId);
        worker.execute(task);
    }

    /**
     * Get worker of connection id.
     *
     * @param connectionId connection Id
     * @return remoting worker
     */
    private static RemotingWorker getWorker(String connectionId) {
        int idx = connectionId.hashCode() & (REMOTING_WORKERS.length - 1);
        return REMOTING_WORKERS[idx];
    }

    public static int workersCount() {
        return REMOTING_WORKERS.length;
    }

}
