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
package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data replicator
 *
 * @author nkorange
 * @author pengzhengfa
 * @since 1.0.0
 */
@Component
@DependsOn("ProtocolManager")
public class DataSyncer {

    private final DataStore dataStore;
    private final GlobalConfig partitionConfig;
    private final Serializer serializer;
    private final DistroMapper distroMapper;
    private final ServerMemberManager memberManager;
    private final int firstRetryCount = 1;
    private final int maxRetryCount = 10;
    private AtomicInteger requestCount = new AtomicInteger(0);
    private Map<String, String> taskMap = new ConcurrentHashMap<>(16);

    public DataSyncer(DataStore dataStore, GlobalConfig partitionConfig,
                      Serializer serializer, DistroMapper distroMapper,
                      ServerMemberManager memberManager) {
        this.dataStore = dataStore;
        this.partitionConfig = partitionConfig;
        this.serializer = serializer;
        this.distroMapper = distroMapper;
        this.memberManager = memberManager;
    }

    @PostConstruct
    public void init() {
        startTimedSync();
    }

    public void submit(SyncTask task, long delay) {

        // If it's a new task:
        if (task.getRetryCount() == 0) {
            Iterator<String> iterator = task.getKeys().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (StringUtils.isNotBlank(taskMap.putIfAbsent(buildKey(key, task.getTargetServer()), key))) {
                    // associated key already exist:
                    if (Loggers.DISTRO.isDebugEnabled()) {
                        Loggers.DISTRO.debug("sync already in process, key: {}", key);
                    }
                    iterator.remove();
                }
            }
        }

        if (task.getKeys().isEmpty()) {
            // all keys are removed:
            return;
        }

        GlobalExecutor.submitDataSync(() -> {
            // 1. check the server
            if (getServers() == null || getServers().isEmpty()) {
                Loggers.SRV_LOG.warn("try to sync data but server list is empty.");
                return;
            }

            List<String> keys = task.getKeys();

            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("try to sync data for this keys {}.", keys);
            }
            // 2. get the datums by keys and check the datum is empty or not
            Map<String, Datum> datumMap = dataStore.batchGet(keys);
            if (datumMap == null || datumMap.isEmpty()) {
                // clear all flags of this task:
                for (String key : keys) {
                    taskMap.remove(buildKey(key, task.getTargetServer()));
                }
                return;
            }

            byte[] data = serializer.serialize(datumMap);

            long timestamp = System.currentTimeMillis();
            boolean success = NamingProxy.syncData(data, task.getTargetServer());
            if (!success) {
                SyncTask syncTask = new SyncTask();
                syncTask.setKeys(task.getKeys());
                syncTask.setRetryCount(task.getRetryCount() + 1);
                syncTask.setLastExecuteTime(timestamp);
                syncTask.setTargetServer(task.getTargetServer());
                retrySync(syncTask);
            } else {
                // clear all flags of this task:
                for (String key : task.getKeys()) {
                    taskMap.remove(buildKey(key, task.getTargetServer()));
                }
            }
        }, delay);
    }

    public void retrySync(SyncTask syncTask) {
        try {
            Member member = new Member();
            long syncRetryDelay;
            for (int retryCount = 0; retryCount < Integer.MAX_VALUE; retryCount++) {
                syncRetryDelay = partitionConfig.getSyncRetryDelay();
                requestCount.incrementAndGet();
                member.setIp(syncTask.getTargetServer().split(":")[0]);
                member.setPort(Integer.parseInt(syncTask.getTargetServer().split(":")[1]));
                if (!getServers().contains(member)) {
                    // if server is no longer in healthy server list, ignore this task:
                    //fix #1665 remove existing tasks
                    if (syncTask.getKeys() != null) {
                        for (String key : syncTask.getKeys()) {
                            taskMap.remove(buildKey(key, syncTask.getTargetServer()));
                        }
                    }
                    return;
                }
                if (requestCount.get() != firstRetryCount) {
                    syncRetryDelay = partitionConfig.getSyncRetryDelay() * retryCount;
                }
                if (requestCount.get() == maxRetryCount) {
                    throw new RuntimeException("You have retried too many times, please contact the system operation and maintenance personnel.");
                }
                submit(syncTask, syncRetryDelay);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startTimedSync() {
        GlobalExecutor.schedulePartitionDataTimedSync(new TimedSync());
    }

    public class TimedSync implements Runnable {

        @Override
        public void run() {

            try {

                if (Loggers.DISTRO.isDebugEnabled()) {
                    Loggers.DISTRO.debug("server list is: {}", getServers());
                }

                // send local timestamps to other servers:
                Map<String, String> keyChecksums = new HashMap<>(64);
                for (String key : dataStore.keys()) {
                    if (!distroMapper.responsible(KeyBuilder.getServiceName(key))) {
                        continue;
                    }

                    Datum datum = dataStore.get(key);
                    if (datum == null) {
                        continue;
                    }
                    keyChecksums.put(key, datum.value.getChecksum());
                }

                if (keyChecksums.isEmpty()) {
                    return;
                }

                if (Loggers.DISTRO.isDebugEnabled()) {
                    Loggers.DISTRO.debug("sync checksums: {}", keyChecksums);
                }

                for (Member member : getServers()) {
                    if (NetUtils.localServer().equals(member.getAddress())) {
                        continue;
                    }
                    NamingProxy.syncCheckSums(keyChecksums, member.getAddress());
                }
            } catch (Exception e) {
                Loggers.DISTRO.error("timed sync task failed.", e);
            }
        }

    }

    public Collection<Member> getServers() {
        return memberManager.allMembers();
    }

    public String buildKey(String key, String targetServer) {
        return key + UtilsAndCommons.CACHE_KEY_SPLITER + targetServer;
    }
}
