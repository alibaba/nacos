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

package com.alibaba.nacos.core.distributed.raft.jraft;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.ap.CPProtocol;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.AbstractConsistencyProtocol;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftEvent;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alipay.sofa.jraft.Node;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftProtocol extends AbstractConsistencyProtocol<RaftConfig> implements CPProtocol<RaftConfig> {

    private volatile boolean isStart = false;

    private JRaftServer raftServer;

    private Node raftNode;

    private Map<String, Properties> metaData = new ConcurrentHashMap<>();

    private NodeManager nodeManager;

    private String selfAddress = InetUtils.getSelfIp();

    @Override
    public void init(RaftConfig config) {

        this.nodeManager = SpringUtils.getBean(NodeManager.class);

        this.selfAddress = nodeManager.self().address();

        NotifyManager.registerPublisher(RaftEvent::new, RaftEvent.class);

        this.raftServer = new JRaftServer(this.nodeManager);
        this.raftServer.init(config, allLogDispacther().values());
        this.raftServer.start();
        isStart = true;

        // There is only one consumer to ensure that the internal consumption
        // is sequential and there is no concurrent competition

        NotifyManager.registerSubscribe(new Subscribe<RaftEvent>() {
            @Override
            public void onEvent(RaftEvent event) {
                final String groupId = event.getGroupId();
                metaData.computeIfAbsent(groupId, s -> new Properties());
                final Properties subMetaData = metaData.get(groupId);
                final String leader = event.getLeader();
                final long term = event.getTerm();
                final List<String> raftClusterInfo = event.getRaftClusterInfo();
                subMetaData.put("leader", leader);
                subMetaData.put("term", term);
                subMetaData.put("raftClusterInfo", raftClusterInfo);
                metaData.put(groupId, subMetaData);
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return RaftEvent.class;
            }
        });

        loadLogDispatcher(config.listLogProcessor());

    }

    @Override
    public Map protocolMetaData() {
        return metaData;
    }

    @Override
    public <R> R metaData(String key, String... subKey) {
        Object o = metaData.get(key);
        if (subKey == null || subKey.length == 0) {
            return (R) o;
        }
        return (R) getVIfMapByRecursive(o, 0, subKey);
    }

    @Override
    public <D> D getData(String key) throws Exception {
        return (D) raftServer.get(key).join();
    }

    @Override
    public boolean submit(Log data) throws Exception {
        CompletableFuture<Boolean> future = submitAsync(data);
        return future.join();
    }

    @Override
    public CompletableFuture<Boolean> submitAsync(Log data) {
        final Throwable[] throwable = new Throwable[] { null };
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            future = raftServer.commit(JLogUtils.toJLog(data, JLog.USER_OPERATION));
        } catch (Throwable e) {
            throwable[0] = e;
        }
        if (Objects.nonNull(throwable[0])) {
            future.completeExceptionally(throwable[0]);
        }
        return future;
    }

    @Override
    public Class<? extends Config> configType() {
        return RaftConfig.class;
    }

    @Override
    public void shutdown() {
        if (isStart) {
            raftServer.shutdown();
        }
    }

    Map<String, LogProcessor> allLogDispacther() {
        return allProcessor();
    }
}
