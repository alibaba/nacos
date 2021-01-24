
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

import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
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
import java.util.HashMap;
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
@DependsOn("serverListManager")
public class DataSyncer {

    @Autowired
    private DataStore dataStore;

    @Autowired
    private GlobalConfig partitionConfig;

    @Autowired
    private Serializer serializer;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private ServerListManager serverListManager;

    private Map<String, String> taskMap = new ConcurrentHashMap<>();

    /**
     * 定时运行TimedSync
     */
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
                /**
                 * 在taskMap中有key的对应  则在task.getKeys()中删除当前key
                 * 如果没有对应的key，则在taskMap新增  com.alibaba.nacos.naming.iplist.ephemeral.public##DEFAULT_GROUP@@userProvide
                 */
                if (StringUtils.isNotBlank(taskMap.putIfAbsent(buildKey(key, task.getTargetServer()), key))) {
                    // associated key already exist:
                    if (Loggers.DISTRO.isDebugEnabled()) {
                        Loggers.DISTRO.debug("sync already in process, key: {}", key);
                    }

                    /**
                     * 当前key对应得数据  可能正在重试中  所以这里剔除
                     */
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
            /**
             * 没有健康的nacos集群节点
             */
            if (getServers() == null || getServers().isEmpty()) {
                Loggers.SRV_LOG.warn("try to sync data but server list is empty.");
                return;
            }

            /**
             * 没有被移除的key
             */
            List<String> keys = task.getKeys();

            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("try to sync data for this keys {}.", keys);
            }
            /**
             * 从本地缓存中批量获取keys对应的Datum
             */
            // 2. get the datums by keys and check the datum is empty or not
            Map<String, Datum> datumMap = dataStore.batchGet(keys);
            /**
             * dataStore已经没有keys的对应  在taskMap中移除key
             */
            if (datumMap == null || datumMap.isEmpty()) {
                // clear all flags of this task:
                for (String key : keys) {
                    taskMap.remove(buildKey(key, task.getTargetServer()));
                }
                return;
            }

            byte[] data = serializer.serialize(datumMap);

            long timestamp = System.currentTimeMillis();
            /**
             * 向TargetServer  发送datumMap数据
             */
            boolean success = NamingProxy.syncData(data, task.getTargetServer());
            if (!success) {
                /**
                 * 失败则重试
                 */
                SyncTask syncTask = new SyncTask();
                syncTask.setKeys(task.getKeys());
                syncTask.setRetryCount(task.getRetryCount() + 1);
                syncTask.setLastExecuteTime(timestamp);
                syncTask.setTargetServer(task.getTargetServer());
                retrySync(syncTask);
            } else {
                /**
                 * 同步成功则移除taskMap
                 */
                // clear all flags of this task:
                for (String key : task.getKeys()) {
                    taskMap.remove(buildKey(key, task.getTargetServer()));
                }
            }
        }, delay);
    }

    /**
     * 重试
     * @param syncTask
     */
    public void retrySync(SyncTask syncTask) {

        Server server = new Server();
        server.setIp(syncTask.getTargetServer().split(":")[0]);
        server.setServePort(Integer.parseInt(syncTask.getTargetServer().split(":")[1]));
        /**
         * syncTask的TargetServer是否是健康的nacos节点
         */
        if (!getServers().contains(server)) {
            // if server is no longer in healthy server list, ignore this task:
            //fix #1665 remove existing tasks
            if (syncTask.getKeys() != null) {
                for (String key : syncTask.getKeys()) {
                    taskMap.remove(buildKey(key, syncTask.getTargetServer()));
                }
            }
            return;
        }

        // TODO may choose other retry policy.
        /**
         * 重试   间隔5000ms
         */
        submit(syncTask, partitionConfig.getSyncRetryDelay());
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
                    /**
                     * 是否由本机节点执行操作
                     */
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

                /**
                 * 遍历所有健康的service
                 */
                for (Server member : getServers()) {
                    /**
                     * 过滤本地节点
                     */
                    if (NetUtils.localServer().equals(member.getKey())) {
                        continue;
                    }

                    /**
                     * 向member发送当前keyChecksums   如果member发现本地keyChecksums不一致   则会向当前节点查询最新的Datum
                     */
                    NamingProxy.syncCheckSums(keyChecksums, member.getKey());
                }
            } catch (Exception e) {
                Loggers.DISTRO.error("timed sync task failed.", e);
            }
        }

    }

    /**
     * 健康的nacos集群节点
     * @return
     */
    public List<Server> getServers() {
        return serverListManager.getHealthyServers();
    }

    /**
     * key@@@@targetServer
     * @param key
     * @param targetServer
     * @return
     */
    public String buildKey(String key, String targetServer) {
        return key + UtilsAndCommons.CACHE_KEY_SPLITER + targetServer;
    }
}
