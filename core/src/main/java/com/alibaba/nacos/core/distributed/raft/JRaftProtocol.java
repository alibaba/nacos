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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.cp.CPKvStore;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.Constants;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.AbstractConsistencyProtocol;
import com.alibaba.nacos.core.distributed.raft.utils.JLog;
import com.alibaba.nacos.core.distributed.raft.utils.JLogUtils;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.ConvertUtils;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alipay.sofa.jraft.Node;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftProtocol extends AbstractConsistencyProtocol<RaftConfig, LogProcessor4CP> implements CPProtocol<RaftConfig> {

    private JRaftServer raftServer;

    private Node raftNode;

    private MemberManager memberManager;

    private String selfAddress = InetUtils.getSelfIp();

    private int failoverRetries;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final AtomicBoolean shutdowned = new AtomicBoolean(false);

    @Override
    public void init(RaftConfig config) {

        if (initialized.compareAndSet(false, true)) {

            // Load all LogProcessor information in advance

            loadLogDispatcher(config.listLogProcessor());

            this.memberManager = SpringUtils.getBean(MemberManager.class);

            this.selfAddress = memberManager.self().address();

            NotifyCenter.registerPublisher(RaftEvent::new, RaftEvent.class);

            this.failoverRetries = ConvertUtils.toInt(config.getVal(RaftSysConstants.REQUEST_FAILOVER_RETRIES), 1);

            this.raftServer = new JRaftServer(this.memberManager, failoverRetries);
            this.raftServer.init(config, config.listLogProcessor());
            this.raftServer.start();

            updateSelfNodeExtendInfo();

            // There is only one consumer to ensure that the internal consumption
            // is sequential and there is no concurrent competition

            NotifyCenter.registerSubscribe(new Subscribe<RaftEvent>() {
                @Override
                public void onEvent(RaftEvent event) {
                    final String groupId = event.getGroupId();
                    Map<String, Map<String, Object>> value = new HashMap<>();
                    Map<String, Object> properties = new HashMap<>();
                    final String leader = event.getLeader();
                    final long term = event.getTerm();
                    final List<String> raftClusterInfo = event.getRaftClusterInfo();

                    // Leader information needs to be selectively updated. If it is valid data,
                    // the information in the protocol metadata is updated.

                    if (StringUtils.isNotBlank(leader)) {
                        properties.put(Constants.LEADER_META_DATA, leader);
                    }
                    properties.put(Constants.TERM_META_DATA, term);
                    properties.put(Constants.RAFT_GROUP_MEMBER, raftClusterInfo);
                    value.put(groupId, properties);
                    metaData.load(value);

                    updateSelfNodeExtendInfo();
                }

                @Override
                public Class<? extends Event> subscribeType() {
                    return RaftEvent.class;
                }
            });
        }
    }

    @Override
    public <R> R metaData(String key, String... subKey) {
        return (R) metaData.get(key, subKey);
    }

    @Override
    public <D> GetResponse<D> getData(GetRequest request) throws Exception {
        int retryCnt = ConvertUtils.toInt(request.getValue(RaftSysConstants.REQUEST_FAILOVER_RETRIES), failoverRetries);
        return (GetResponse<D>) raftServer.get(request, retryCnt).get();
    }

    @Override
    public boolean submit(Log data) throws Exception {
        CompletableFuture<Boolean> future = submitAsync(data);
        Boolean result = future.join();
        if (result == null) {
            return false;
        }
        return result;
    }

    @Override
    public CompletableFuture<Boolean> submitAsync(Log data) {
        int retryCnt = ConvertUtils.toInt(data.extendVal(RaftSysConstants.REQUEST_FAILOVER_RETRIES), failoverRetries);
        final Throwable[] throwable = new Throwable[] { null };
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            raftServer.commit(JLogUtils.toJLog(data, JLog.JLogOperaton.MODIFY_OPERATION), future, retryCnt);
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
        if (initialized.get() && shutdowned.compareAndSet(false, true)) {
            raftServer.shutdown();
        }
    }

    @Override
    public <D> CPKvStore<D> createKVStore(String storeName, Serializer serializer, SnapshotOperation snapshotOperation) {
        RaftKVStore<D> kvStore = new RaftKVStore<D>(storeName, serializer, snapshotOperation);

        // Because Raft uses RaftProtocol internally, so LogProcessor is implemented, need to add

        LogProcessor4CP processor = kvStore.getLogProcessor();

        processor.injectProtocol(this);

        this.raftServer.createMultiRaftGroup(Collections.singletonList(processor));
        return kvStore;
    }

    void updateSelfNodeExtendInfo() {
        Member member = memberManager.self();
        member.setExtendVal("raft", metaData);
        memberManager.update(member);
    }

}
