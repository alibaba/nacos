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

package com.alibaba.nacos.core.distributed.distro.core;

import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.DistroSysConstants;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.distributed.distro.utils.DistroUtils;
import com.alibaba.nacos.core.utils.ConvertUtils;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class TaskCenter {

    private final int cpuCoreCount;
    private final DistroConfig config;
    private DataSyncer dataSyncer;
    private List<Worker> workers = new ArrayList<>();
    private volatile boolean shutdown = false;
    private AtomicBoolean initialize = new AtomicBoolean(false);

    public TaskCenter(DistroConfig config, DataSyncer dataSyncer) {
        this.config = config;
        this.dataSyncer = dataSyncer;
        this.cpuCoreCount = Runtime.getRuntime().availableProcessors();
    }

    public void start() {
        if (initialize.compareAndSet(false, true)) {
            for (int i = 0; i < cpuCoreCount; i++) {
                Worker worker = new Worker(i);
                workers.add(worker);
                DistroExecutor.executeWorker(worker);
            }
        }
    }

    public void addTask(String key) {
        workers.get(DistroUtils.shakeUp(key, cpuCoreCount)).addTask(key);
    }

    public void shutdown() {
        shutdown = true;
    }

    private int getBatchSyncKeyCount() {
        return ConvertUtils
                .toInt(
                        config.getVal(DistroSysConstants.BATCH_SYNC_KEY_COUNT),
                        DistroSysConstants.DEFAULT_BATCH_SYNC_KEY_COUNT);
    }

    private long getTaskDispatchPeriod() {
        return ConvertUtils
                .toLong(
                        config.getVal(DistroSysConstants.TASK_DISPATCH_PERIOD_MS),
                        DistroSysConstants.DEFAULT_TASK_DISPATCH_PERIOD);
    }

    class Worker implements Runnable {

        private final int index;

        private int dataSize = 0;

        private long lastDispatchTime = 0L;

        private BlockingQueue<String> queue = new LinkedBlockingQueue<>(128 * 1024);

        private Worker(int index) {
            this.index = index;
        }

        public void addTask(String key) {
            queue.offer(key);
        }

        public int getIndex() {
            return index;
        }

        @Override
        public void run() {
            int batchSyncKeyCount = getBatchSyncKeyCount();
            long taskDispatchPeriod = getTaskDispatchPeriod();
            List<String> keys = new ArrayList<>();
            for (; ; ) {

                if (shutdown) {
                    queue.clear();
                    return;
                }

                try {
                    String key = queue.poll(1000, TimeUnit.MILLISECONDS);

                    if (CollectionUtils.isEmpty(config.getMembers())) {
                        continue;
                    }

                    if (StringUtils.isBlank(key)) {
                        continue;
                    }

                    if (dataSize == 0) {
                        keys = new ArrayList<>();
                    }

                    keys.add(key);
                    dataSize++;

                    if (dataSize == batchSyncKeyCount ||
                            (System.currentTimeMillis() - lastDispatchTime) > taskDispatchPeriod) {

                        final String self = config.getSelfMember();

                        for (String member : config.getMembers()) {
                            if (Objects.equals(self, member)) {
                                continue;
                            }
                            SyncTask syncTask = new SyncTask();
                            syncTask.setKeys(keys);
                            syncTask.setTargetServer(member);

                            dataSyncer.submit(syncTask, 0);
                        }
                        lastDispatchTime = System.currentTimeMillis();
                        dataSize = 0;

                        // TODO to support auto-refresh by operations

                        batchSyncKeyCount = getBatchSyncKeyCount();
                        taskDispatchPeriod = getTaskDispatchPeriod();
                    }

                } catch (Exception e) {
                    Loggers.DISTRO.error("worker [{}] execute has error : {}", index, e);
                }
            }
        }
    }

}
