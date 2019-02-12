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
package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Data sync task dispatcher
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class TaskDispatcher {

    private List<BlockingQueue<String>> taskList = new ArrayList<>();

    @Autowired
    private PartitionConfig partitionConfig;

    @Autowired
    private DataSyncer dataSyncer;

    @PostConstruct
    public void init() {
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            taskList.add(new LinkedBlockingQueue<>(128 * 1024));
            GlobalExecutor.submitTaskDispatch(new TaskScheduler(i));
        }
    }

    public int mapTask(String key) {
        return Math.abs(key.hashCode() % Integer.MAX_VALUE) % Runtime.getRuntime().availableProcessors();
    }

    public void addTask(String key) {
        taskList.get(mapTask(key)).add(key);
    }

    public class TaskScheduler implements Runnable {

        private int index;

        private int dataSize = 0;

        private long lastDispatchTime = 0L;

        public TaskScheduler(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public void run() {

            List<String> keys = new ArrayList<>();
            while (true) {

                try {

                    String key = taskList.get(index).take();

                    if (dataSize == 0) {
                        keys = new ArrayList<>();
                    }

                    if (dataSize < partitionConfig.getBatchSyncKeyCount()) {
                        keys.add(key);
                        dataSize++;
                    }

                    if (dataSize == partitionConfig.getBatchSyncKeyCount() ||
                        (System.currentTimeMillis() - lastDispatchTime) > partitionConfig.getTaskDispatchPeriod()) {

                        for (Server member : dataSyncer.getServers()) {
                            if (NetUtils.localServer().equals(member.getKey())) {
                                continue;
                            }
                            SyncTask syncTask = new SyncTask();
                            syncTask.setKeys(keys);
                            syncTask.setTargetServer(member.getKey());
                            dataSyncer.submit(syncTask, 0);
                        }
                        lastDispatchTime = System.currentTimeMillis();
                        dataSize = 0;
                    }

                } catch (Exception e) {
                    Loggers.EPHEMERAL.error("dispatch sync task failed.", e);
                }
            }
        }
    }
}
