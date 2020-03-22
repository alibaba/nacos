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

import com.alibaba.nacos.core.distributed.DistroMapper;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.DistroKVStore;
import com.alibaba.nacos.core.distributed.distro.KVManager;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class PartitionDataTimedSync {

    private final KVManager kvManager;
    private final DistroMapper distroMapper;
    private final DistroClient distroClient;
    private final DistroConfig distroConfig;

    private volatile boolean shutdown = false;

    private Worker worker;

    public PartitionDataTimedSync(
            KVManager kvManager,
            DistroMapper distroMapper, DistroConfig distroConfig, DistroClient distroClient) {
        this.kvManager = kvManager;
        this.distroMapper = distroMapper;
        this.distroConfig = distroConfig;
        this.distroClient = distroClient;
    }

    public void start() {
        this.worker = new Worker();
        DistroExecutor.schedulePartitionDataTimedSync(worker, TimeUnit.SECONDS.toMillis(5));
    }

    public void shutdown() {
        shutdown = true;
    }

    public Collection<String> getServers() {
        return distroConfig.getMembers();
    }

    private String buildKey(String key, String targetServer) {
        return key + "@@@@" + targetServer;
    }

    private class Worker implements Runnable {

        @Override
        public void run() {

            if (shutdown) {
                Loggers.DISTRO.warn("Closing task...");
                return;
            }

            try {
                final Collection<String> members = getServers();
                Loggers.DISTRO.debug("server list is: {}", members);
                final Map<String, DistroKVStore> kvStoreMap = kvManager.list();
                kvStoreMap.forEach(new BiConsumer<String, DistroKVStore>() {
                    @Override
                    public void accept(String biz, DistroKVStore dataStore) {
                        final Map<String, Map<String, String>> keyChecksums = new HashMap<>(kvStoreMap.size());
                        Map<String, String> subKeyChecksums = new HashMap<>(64);

                        // send local timestamps to other servers:

                        for (Object key : dataStore.allKeys()) {
                            if (!distroMapper.responsible((String) key)) {
                                continue;
                            }

                            String checkSum = dataStore.get((String) key).getChecksum();
                            if (checkSum == null) {
                                continue;
                            }
                            subKeyChecksums.put((String) key, checkSum);
                        }

                        if (subKeyChecksums.isEmpty()) {
                            return;
                        }

                        Loggers.DISTRO.debug("sync checksums: {}", keyChecksums);

                        // The information of different biz should be independent of each other,
                        // as is the data synchronization. The different biz does not affect each
                        // other, to avoid affecting the data synchronization of other normal
                        // biz because of an error.

                        keyChecksums.put(biz, subKeyChecksums);
                        for (String member : members) {
                            if (Objects.equals(distroConfig.getSelfMember(), member)) {
                                continue;
                            }
                            distroClient.syncCheckSums(keyChecksums, member);
                        }
                    }
                });
            } catch (Exception e) {
                Loggers.DISTRO.error("timed sync task failed.", e);
            } finally {
                DistroExecutor.schedulePartitionDataTimedSync(worker, TimeUnit.SECONDS.toMillis(5));
            }
        }
    }

}
