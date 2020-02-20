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
import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.DistroKVStore;
import com.alibaba.nacos.core.distributed.distro.KVManager;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroServer {

    private DataSyncer dataSyncer;
    private PartitionDataTimedSync timedSync;
    private TaskCenter taskCenter;
    private DistroClient distroClient;

    private boolean initialized = false;

    private final KVManager kvManager;
    private final DistroConfig config;
    private final NodeManager nodeManager;
    private final DistroMapper distroMapper;

    private final Map<String, String> syncChecksumTasks = new ConcurrentHashMap<>(16);

    private Serializer serializer;

    public DistroServer(
            final NodeManager nodeManager,
            final KVManager kvManager,
            final DistroConfig config) {
        this.nodeManager = nodeManager;
        this.config = config;
        this.serializer = SerializeFactory.getDefault();

        this.distroMapper = new DistroMapper(nodeManager, config);
        this.kvManager = kvManager;
    }

    public void start() {

        // === start:Initialize related members of the distro protocol

        this.distroClient = new DistroClient(
                this.nodeManager,
                this.serializer);

        this.timedSync = new PartitionDataTimedSync(
                this.kvManager,
                this.distroMapper,
                this.nodeManager,
                this.distroClient);

        this.dataSyncer = new DataSyncer(
                SpringUtils.getBean(NodeManager.class),
                this.kvManager,
                this.distroClient);

        this.taskCenter = new TaskCenter(this.nodeManager, this.dataSyncer);

        // === end

        // === start:Start relevant members of the distro protocol

        DistroExecutor.executeByGlobal(() -> {
            try {

                // sync data from remote node

                load();
            } catch (Exception e) {
                Loggers.DISTRO.error("load data failed.", e);
            }
        });

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
            Loggers.DISTRO.error("sync full data from " + server.address() + " failed!", ExceptionUtil.getAllExceptionMsg(e));
            return false;
        }
    }

    public void onReceiveChecksums(Map<String, Map<String, String>> checksumMap, String server) {
        if (syncChecksumTasks.containsKey(server)) {
            // Already in process of this server:
            Loggers.DISTRO.warn("sync checksum task already in process with {}", server);
            return;
        }

        syncChecksumTasks.put(server, "1");
        try {

            List<String> toUpdateKeys = new ArrayList<>();
            List<String> toRemoveKeys = new ArrayList<>();
            for (Map.Entry<String, Map<String, String>> entry : checksumMap.entrySet()) {
                final String storeName = entry.getKey();
                final DistroKVStore dataStore = kvManager.get(storeName);
                Map<String, String> checkSumMap = entry.getValue();
                for (Map.Entry<String, String> item : checkSumMap.entrySet()) {
                    final String key = item.getKey();
                    if (distroMapper.responsible(item.getKey())) {
                        // this key should not be sent from remote server:
                        Loggers.DISTRO.error("receive responsible key timestamp of " + entry.getKey() + " from " + server);
                        // abort the procedure:
                        return;
                    }

                    if (!dataStore.contains(key) ||
                            dataStore.getByKey(key) == null ||
                            !Objects.equals(dataStore.getCheckSum(key), item.getValue())) {
                        toUpdateKeys.add(entry.getKey());
                    }
                }

                for (Object key : dataStore.allKeys()) {

                    if (!server.equals(distroMapper.mapSrv((String) key))) {
                        continue;
                    }

                    if (!checksumMap.containsKey(key)) {
                        toRemoveKeys.add((String) key);
                    }
                }

                if (Loggers.DISTRO.isDebugEnabled()) {
                    Loggers.DISTRO.info("to remove keys: {}, to update keys: {}, source: {}", toRemoveKeys, toUpdateKeys, server);
                }

                for (String key : toRemoveKeys) {
                    dataStore.operate(key, null, DistroKVStore.REMOVE_COMMAND);
                }

                if (toUpdateKeys.isEmpty()) {
                    return;
                }

                try {
                    byte[] result = distroClient.getData(storeName, toUpdateKeys, server);
                    processData(result);
                } catch (Exception e) {
                    Loggers.DISTRO.error("get data from " + server + " failed!", e);
                }
            }
        } finally {
            // Remove this 'in process' flag:
            syncChecksumTasks.remove(server);
        }

    }

    private void processData(byte[] data) throws Exception {
        if (data.length > 0) {
            Map<String, Map<String, KVStore.Item>> allDataMap = JSON.parseObject(data,
                    new TypeReference<Map<String, Map<String, KVStore.Item>>>() {
                    }.getType());
            allDataMap.forEach((bizInfo, map) -> DistroExecutor.executeByGlobal(() -> {
                final DistroKVStore store = kvManager.get(bizInfo);
                if (Objects.isNull(store)) {
                    return;
                }
                store.load(map);
            }));
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

    public DistroMapper getDistroMapper() {
        return distroMapper;
    }

    public KVManager getKvManager() {
        return kvManager;
    }
}
