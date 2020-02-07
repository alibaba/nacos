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

package com.alibaba.nacos.core.distributed.distro.sync;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.param.Body;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpResResult;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.AbstractDistroKVStore;
import com.alibaba.nacos.core.distributed.store.Record;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.core.utils.Constants.NACOS_SERVER_HEADER;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DataSyncer {

    private final NodeManager nodeManager;

    private final List<AbstractDistroKVStore> distroStores;

    private final Map<String, String> taskMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService dataSyncExecutor;

    private NSyncHttpClient httpClient;

    public DataSyncer(NodeManager nodeManager,
                      List<AbstractDistroKVStore> distroStores) {
        this.nodeManager = nodeManager;
        this.distroStores = distroStores;
    }

    public void start() {
        this.dataSyncExecutor = ExecutorFactory.newScheduledExecutorService(DataSyncer.class.getCanonicalName(),
                Runtime.getRuntime().availableProcessors(),
                new NameThreadFactory("com.alibaba.nacos.naming.distro.data.syncer"));

        this.httpClient = HttpClientManager.newHttpClient(DataSyncer.class.getCanonicalName());
    }

    public void submit(SyncTask task, long delay) {
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

        for (AbstractDistroKVStore distroStore : distroStores) {
            dataSyncExecutor.schedule(() -> {
                // 1. check the server
                if (getServers() == null || getServers().isEmpty()) {
                    return;
                }

                List<String> keys = task.getKeys();


                // 2. get the datums by keys and check the datum is empty or not
                Map<String, ? extends Record> datumMap = distroStore.batchGet(keys);
                if (datumMap == null || datumMap.isEmpty()) {
                    // clear all flags of this task:
                    for (String key : keys) {
                        taskMap.remove(buildKey(key, task.getTargetServer()));
                    }
                    return;
                }

                long timestamp = System.currentTimeMillis();
                boolean success = syncData(datumMap, task.getTargetServer());
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
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    public void retrySync(SyncTask syncTask) {

        if (!nodeManager.hasNode(syncTask.getTargetServer())) {
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
        submit(syncTask, 2000);
    }

    public List<Node> getServers() {
        return nodeManager.allNodes();
    }

    public void shutdown() {

    }

    private String buildKey(String key, String targetServer) {
        return key + "@@@@" + targetServer;
    }

    private boolean syncData(Map<String, ? extends Record> data, String curServer) {
        final Header header = Header.newInstance()
                .addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION)
                .addParam(HttpHeaderConsts.USER_AGENT_HEADER, NACOS_SERVER_HEADER + ":" + VersionUtils.VERSION)
                .addParam("Accept-Encoding", "gzip,deflate,sdch")
                .addParam("Connection", "Keep-Alive")
                .addParam("Content-Encoding", "gzip");

        try {
            final String url = "http://" + curServer + nodeManager.getContextPath() + "/distro/datum";
            HttpResResult<String> result = (HttpResResult<String>) httpClient.post(url, header, Query.EMPTY, Body.objToBody(data), new TypeReference<ResResult<String>>() {
            });
            if (HttpURLConnection.HTTP_OK == result.getHttpCode()) {
                return true;
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.getHttpCode()) {
                return true;
            }
            throw new IOException("failed to req API:" + url + ". code:"
                    + result.getData() + " msg: " + result.getData());
        } catch (Exception e) {
        }
        return false;
    }

}
