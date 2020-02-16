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

import com.alibaba.nacos.consistency.cluster.Node;
import com.alibaba.nacos.consistency.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.AbstractDistroKVStore;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.utils.Loggers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class PartitionDataTimedSync {

    private final Map<String, AbstractDistroKVStore> distroStores;
    private final DistroMapper distroMapper;
    private final NodeManager nodeManager;
    private final DistroClient distroClient;

    private Worker worker;

    public PartitionDataTimedSync(
            Map<String, AbstractDistroKVStore> distroStores,
            DistroMapper distroMapper, NodeManager nodeManager, DistroClient distroClient) {
        this.distroStores = distroStores;
        this.distroMapper = distroMapper;
        this.nodeManager = nodeManager;
        this.distroClient = distroClient;
    }

    public void start() {
        this.worker = new Worker();
        DistroExecutor.schedulePartitionDataTimedSync(worker);
    }

    public void shutdown() {

    }

    private class Worker implements Runnable {

        @Override
        public void run() {
            try {

                final List<Node> nodes = getServers();

                if (Loggers.DISTRO.isDebugEnabled()) {
                    Loggers.DISTRO.debug("server list is: {}", nodes);
                }

                final Map<String, Map<String, String>> keyChecksums = new HashMap<>(64);

                distroStores.forEach(new BiConsumer<String, AbstractDistroKVStore>() {
                    @Override
                    public void accept(String s, AbstractDistroKVStore dataStore) {

                       Map<String, String> subKeyChecksums = new HashMap<>(64);

                        // send local timestamps to other servers:

                        for (Object key : dataStore.allKeys()) {
                            if (!distroMapper.responsible((String) key)) {
                                continue;
                            }

                            String checkSum = dataStore.getCheckSum((String) key);
                            if (checkSum == null) {
                                continue;
                            }
                            subKeyChecksums.put((String) key, checkSum);
                        }

                        if (subKeyChecksums.isEmpty()) {
                            return;
                        }

                        if (Loggers.DISTRO.isDebugEnabled()) {
                            Loggers.DISTRO.debug("sync checksums: {}", keyChecksums);
                        }

                        keyChecksums.put(s, subKeyChecksums);

                    }
                });

                // TODO 是否可以每一个 store 单独去同步，而不是收集完全部的数据后在进行 sync checkSums

                for (Node member : nodes) {
                    if (Objects.equals(nodeManager.self(), member)) {
                        continue;
                    }
                    distroClient.syncCheckSums(keyChecksums, member.address());
                }

            } catch (Exception e) {
                Loggers.DISTRO.error("timed sync task failed.", e);
            }
        }
    }

    public List<Node> getServers() {
        return nodeManager.allNodes();
    }

    private String buildKey(String key, String targetServer) {
        return key + "@@@@" + targetServer;
    }

}
