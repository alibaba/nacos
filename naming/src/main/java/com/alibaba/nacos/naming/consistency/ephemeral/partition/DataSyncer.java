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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.members.Member;
import com.alibaba.nacos.naming.cluster.members.MemberChangeListener;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data replicator
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class DataSyncer implements MemberChangeListener {

    @Autowired
    private DataStore dataStore;

    @Autowired
    private Serializer serializer;

    @Autowired
    private ServerListManager serverListManager;

    private Map<String, String> taskMap = new ConcurrentHashMap<>();

    private List<Member> servers;

    public DataSyncer() {
        serverListManager.listen(this);
        startTimedSync();
    }

    public void submit(SyncTask task) {

        Iterator<String> iterator = task.getKeys().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (StringUtils.isNotBlank(taskMap.putIfAbsent(buildKey(key, task.getTargetServer()), key))) {
                // associated key already exist:
                iterator.remove();
            }
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
                    Map<String, List<Instance>> instancMap = dataStore.batchGet(keys);
                    byte[] data = serializer.serialize(instancMap);

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

        }
    }

    public List<Member> getServers() {
        return servers;
    }

    public String buildKey(String key, String targetServer) {
        return key + UtilsAndCommons.CACHE_KEY_SPLITER + targetServer;
    }

    @Override
    public void onChangeMemberList(List<Member> latestMembers) {

    }

    @Override
    public void onChangeReachableMemberList(List<Member> latestReachableMembers) {
        servers = latestReachableMembers;
    }
}
