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

package com.alibaba.nacos.core.distributed.distro.task.execute;

/**
 * Distro execute workers manager.
 *
 * @author xiweng.yy
 */
public final class DistroExecuteWorkersManager {
    
    private final DistroExecuteWorker[] connectionWorkers;

    public DistroExecuteWorkersManager() {
        int workerCount = findWorkerCount();
        connectionWorkers = new DistroExecuteWorker[workerCount];
        for (int mod = 0; mod < workerCount; ++mod) {
            connectionWorkers[mod] = new DistroExecuteWorker(mod, workerCount);
        }
    }
    
    private int findWorkerCount() {
        final int coreCount = Runtime.getRuntime().availableProcessors();
        int result = 1;
        while (result < coreCount) {
            result <<= 1;
        }
        return result;
    }
    
    /**
     * Dispatch task to worker by tag.
     */
    public void dispatch(Object tag, Runnable task) {
        DistroExecuteWorker worker = getWorker(tag);
        worker.execute(task);
    }
    
    private DistroExecuteWorker getWorker(Object tag) {
        int idx = (tag.hashCode() & Integer.MAX_VALUE) % workersCount();
        return connectionWorkers[idx];
    }
    
    /**
     * Get workers status.
     *
     * @return workers status string
     */
    public String workersStatus() {
        StringBuilder sb = new StringBuilder();
        for (DistroExecuteWorker worker : connectionWorkers) {
            sb.append(worker.status()).append("\n");
        }
        return sb.toString();
    }

    public int workersCount() {
        return connectionWorkers.length;
    }

}
