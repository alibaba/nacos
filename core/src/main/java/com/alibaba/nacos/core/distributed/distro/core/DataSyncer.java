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

import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.DistroSysConstants;
import com.alibaba.nacos.core.distributed.distro.KVManager;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DataSyncer {

    private final MemberManager memberManager;

    private final KVManager kvManager;

    private final DistroClient distroClient;

    private final Map<String, String> taskMap = new ConcurrentHashMap<>();

    private final DistroConfig config;

    private RetryPolicy policy;

    private AtomicBoolean isStarted = new AtomicBoolean(false);

    public DataSyncer(
            DistroConfig config,
            MemberManager memberManager,
            KVManager kvManager,
            DistroClient distroClient) {
        this.config = config;
        this.memberManager = memberManager;
        this.kvManager = kvManager;
        this.distroClient = distroClient;
    }

    public void start() {
        isStarted.compareAndSet(false, true);
    }

    public void submit(SyncTask task, long delay) {

        if (!isStarted.get()) {
            throw new IllegalStateException("Syncer does not call the start method or the shutdown method has been executed");
        }

        // If it's a new task:
        if (task.getRetryCount() == 0) {
            // associated key already exist:
            // TODO log debug
            task.getKeys().removeIf(key -> StringUtils.isNotBlank(taskMap.putIfAbsent(buildKey(key, task.getTargetServer()), key)));
        }

        if (task.getKeys().isEmpty()) {
            // all keys are removed:
            return;
        }

        kvManager.list().forEach((s, distroStore) ->
                DistroExecutor.scheduleDataSync(() -> {
                    // 1. check the server
                    if (getServers() == null || getServers().isEmpty()) {
                        return;
                    }

                    List<String> keys = task.getKeys();

                    Map<String, Map<String, KVStore.Item>> syncData = new HashMap<>(4);

                    // 2. get the datums by keys and check the datum is empty or not
                    Map<String, KVStore.Item> itemMap = distroStore.getItemByBatch(keys);
                    if (itemMap.isEmpty()) {
                        // clear all flags of this task:
                        for (String key : keys) {
                            taskMap.remove(buildKey(key, task.getTargetServer()));
                        }
                        return;
                    }

                    syncData.put(distroStore.storeName(), itemMap);

                    long timestamp = System.currentTimeMillis();
                    boolean success = distroClient.syncData(syncData, task.getTargetServer());
                    if (success) {
                        // clear all flags of this task:
                        for (String key : task.getKeys()) {
                            taskMap.remove(buildKey(key, task.getTargetServer()));
                        }
                    } else {
                        SyncTask syncTask = new SyncTask();
                        syncTask.setBizInfo(distroStore.storeName());
                        syncTask.setKeys(task.getKeys());
                        syncTask.setRetryCount(task.getRetryCount() + 1);
                        syncTask.setLastExecuteTime(timestamp);
                        syncTask.setTargetServer(task.getTargetServer());
                        retrySync(syncTask);
                    }
                }, delay, TimeUnit.MILLISECONDS));
    }

    public void retrySync(SyncTask syncTask) {

        if (!memberManager.hasMember(syncTask.getTargetServer())) {
            // if server is no longer in healthy server list, ignore this task:
            //fix #1665 remove existing tasks
            if (syncTask.getKeys() != null) {
                for (String key : syncTask.getKeys()) {
                    taskMap.remove(buildKey(key, syncTask.getTargetServer()));
                }
            }
            return;
        }

        // TODO support auto-refresh policy impl
        getRetryPolicy().retryTask(syncTask);
    }

    public Collection<Member> getServers() {
        return memberManager.allMembers();
    }

    public void shutdown() {
        isStarted.compareAndSet(true, false);
    }

    private RetryPolicy getRetryPolicy() {
        if (policy == null) {
            synchronized (this) {
                if (policy == null) {
                    final String name = config.getValOfDefault(DistroSysConstants.RETRY_SYNC_POLICY,
                            DistroSysConstants.DEFAULT_RETRY_SYNC_POLICY);
                    ServiceLoader<RetryPolicy> loader = ServiceLoader.load(RetryPolicy.class);
                    for (RetryPolicy retryPolicy : loader) {
                        if (Objects.equals(retryPolicy.name(), name)) {
                            policy = retryPolicy;
                        }
                    }
                    if (policy == null) {
                        policy = new SimpleDelayRetryPolicy();
                    }
                    policy.injectDataSyncer(this);
                }
            }
        }
        return policy;
    }

    private String buildKey(String key, String targetServer) {
        return key + "@@@@" + targetServer;
    }

}
