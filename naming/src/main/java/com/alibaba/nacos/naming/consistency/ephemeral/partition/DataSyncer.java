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

import com.alibaba.nacos.common.util.IoUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.servers.ServerChangeListener;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.CharEncoding.UTF_8;

/**
 * Data replicator
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
@DependsOn("serverListManager")
public class DataSyncer implements ServerChangeListener {

    @Autowired
    private DataStore dataStore;

    @Autowired
    private PartitionConfig partitionConfig;

    @Autowired
    private Serializer serializer;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private ServerListManager serverListManager;

    private Map<String, String> taskMap = new ConcurrentHashMap<>();

    private List<Server> servers;

    private boolean initialized = false;

    @PostConstruct
    public void init() {
        serverListManager.listen(this);
        startTimedSync();
    }

    public void submit(SyncTask task) {

        // If it's a new task:
        if (task.getRetryCount() == 0) {
            Iterator<String> iterator = task.getKeys().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (StringUtils.isNotBlank(taskMap.putIfAbsent(buildKey(key, task.getTargetServer()), key))) {
                    // associated key already exist:
                    iterator.remove();
                }
            }
        }

        if (task.getKeys().isEmpty()) {
            // all keys are removed:
            return;
        }

        GlobalExecutor.submitDataSync(new Runnable() {
            @Override
            public void run() {

                try {
                    if (servers == null || servers.isEmpty()) {
                        Loggers.SRV_LOG.warn("try to sync data but server list is empty.");
                        return;
                    }

                    List<String> keys = task.getKeys();

                    Loggers.EPHEMERAL.info("sync keys: {}", keys);

                    Map<String, Datum<?>> datumMap = dataStore.batchGet(keys);

                    if (datumMap == null || datumMap.isEmpty()) {
                        // clear all flags of this task:
                        for (String key : task.getKeys()) {
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
                        submit(syncTask);
                    } else {
                        // clear all flags of this task:
                        for (String key : task.getKeys()) {
                            taskMap.remove(buildKey(key, task.getTargetServer()));
                        }
                    }

                } catch (Exception e) {
                    Loggers.SRV_LOG.error("sync data failed.", e);
                }
            }
        });
    }

    public void startTimedSync() {
        GlobalExecutor.schedulePartitionDataTimedSync(new TimedSync());
    }

    public class TimedSync implements Runnable {

        @Override
        public void run() {

            try {

                File metaFile = new File(UtilsAndCommons.DATA_BASE_DIR + File.separator + "ephemeral.properties");
                if (initialized) {
                    // write the current instance count to disk:
                    IoUtils.writeStringToFile(metaFile, "instanceCount=" + dataStore.keys().size(), "UTF-8");
                } else {
                    // check if most of the data are loaded:
                    List<String> lines = IoUtils.readLines(new InputStreamReader(new FileInputStream(metaFile), UTF_8));
                    if (lines == null || lines.isEmpty()) {
                        initialized = true;
                    } else {
                        int desiredInstanceCount = Integer.parseInt(lines.get(0).split("=")[1]);
                        if (desiredInstanceCount * partitionConfig.getInitDataRatio() < dataStore.keys().size()) {
                            initialized = true;
                        }
                    }
                }

            } catch (Exception e) {
                Loggers.EPHEMERAL.error("operate on meta file failed.", e);
            }

            try {
                // send local timestamps to other servers:
                Map<String, Long> keyTimestamps = new HashMap<>(64);
                for (String key : dataStore.keys()) {
                    if (!distroMapper.responsible(KeyBuilder.getServiceName(key))) {
                        continue;
                    }
                    keyTimestamps.put(key, dataStore.get(key).timestamp.get());
                }

                if (keyTimestamps.isEmpty()) {
                    return;
                }

                for (Server member : servers) {
                    if (NetUtils.localServer().equals(member.getKey())) {
                        continue;
                    }
                    NamingProxy.syncTimestamps(keyTimestamps, member.getKey());
                }
            } catch (Exception e) {
                Loggers.EPHEMERAL.error("timed sync task failed.", e);
            }
        }
    }

    public List<Server> getServers() {
        return servers;
    }

    public String buildKey(String key, String targetServer) {
        return key + UtilsAndCommons.CACHE_KEY_SPLITER + targetServer;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void onChangeServerList(List<Server> latestMembers) {

    }

    @Override
    public void onChangeHealthyServerList(List<Server> healthServers) {
        servers = healthServers;
    }
}
