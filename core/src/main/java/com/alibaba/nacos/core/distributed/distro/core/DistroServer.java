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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.AbstractDistroKVStore;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.DistroSysConstants;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SerializeFactory;
import com.alibaba.nacos.core.utils.Serializer;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DistroServer {

    private DataSyncer dataSyncer;
    private PartitionDataTimedSync timedSync;
    private TaskCenter taskCenter;
    private DistroClient distroClient;

    private boolean initialized = false;

    private final Map<String, AbstractDistroKVStore> distroStores;
    private final DistroConfig config;
    private final NodeManager nodeManager;

    private Serializer serializer;

    public DistroServer(
            final NodeManager nodeManager,
            final List<AbstractDistroKVStore> distroStores,
            final DistroConfig config) {
        this.nodeManager = nodeManager;
        this.config = config;
        this.serializer = SerializeFactory.getSerializerDefaultJson(
                config.getValOfDefault(DistroSysConstants.DATA_SERIALIZER_TYPE, SerializeFactory.JSON_INDEX));

        Map<String, AbstractDistroKVStore> tmp = new HashMap<>();
        distroStores.forEach(store -> tmp.put(store.biz(), store));

        this.distroStores = new ConcurrentHashMap<>(tmp);
    }

    public void start() {

        DistroExecutor.executeByGlobal(() -> {
            try {
                load();
            } catch (Exception e) {
                Loggers.DISTRO.error("load data failed.", e);
            }
        });

        this.distroClient = new DistroClient(nodeManager, serializer);

        this.timedSync = new PartitionDataTimedSync(this.distroStores);
        this.dataSyncer = new DataSyncer(SpringUtils.getBean(NodeManager.class), distroStores, distroClient);
        this.taskCenter = new TaskCenter(this.nodeManager, this.dataSyncer);

        this.dataSyncer.start();
        this.timedSync.start();
        this.taskCenter.start();
    }

    private void load() throws Exception {
        if (SystemUtils.STANDALONE_MODE) {
            initialized = true;
            return;
        }
        while (nodeManager.allNodes().size() <= 1) {
            Thread.sleep(1000L);
            Loggers.DISTRO.info("waiting server list init...");
        }

        for (Node server : nodeManager.allNodes()) {
            if (Objects.equals(nodeManager.self().address(), server.address())) {
                continue;
            }
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.debug("sync from " + server);
            }
            // try sync data from remote server:
            if (syncAllDataFromRemote(server)) {
                initialized = true;
                return;
            }
        }
    }



    public boolean submit(Log log) {
        final String key = log.getKey();
        taskCenter.addTask(key);
        return true;
    }

    private boolean syncAllDataFromRemote(Node server) {

        try {
            byte[] data = distroClient.getAllData(server.address());
            processData(data);
            return true;
        } catch (Exception e) {
            Loggers.DISTRO.error("sync full data from " + server + " failed!", e);
            return false;
        }
    }

    private void processData(byte[] data) throws Exception {
        if (data.length > 0) {
            Map<String, Map<String, byte[]>> allDataMap = JSON.parseObject(data,
                    new TypeReference<Map<String, Map<String, byte[]>>>(){}.getType());
            allDataMap.entrySet().parallelStream()
                    .forEach(entry -> {
                        final String bizInfo = entry.getKey();
                        final Map<String, byte[]> value = entry.getValue();
                        final AbstractDistroKVStore store = distroStores.get(bizInfo);
                        if (Objects.isNull(store)) {
                            return;
                        }
                        store.load(value);
                    });
        }
    }

    public void shutdown() {
        if (Objects.nonNull(timedSync)) {
            timedSync.shutdown();
        }
        if (Objects.nonNull(dataSyncer)) {
            dataSyncer.shutdown();
        }
        if (Objects.nonNull(taskCenter)) {
            taskCenter.shutdown();
        }
    }
}
