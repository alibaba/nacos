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

import com.alibaba.nacos.core.distributed.BizProcessor;
import com.alibaba.nacos.core.distributed.raft.RaftEvent;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class BaseStateMachine extends StateMachineAdapter {

    private final AtomicLong leaderTerm = new AtomicLong(-1L);

    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    protected Map<String, BizProcessor> processorMap = new HashMap<>();

    private Collection<SnapshotOperate> snapshotOperates;

    private NodeManager nodeManager = NodeManager.getInstance();

    public BaseStateMachine() {
        snapshotOperates = SpringUtils.getBeansOfType(SnapshotOperate.class).values();
    }

    public synchronized void registerBizProcessor(BizProcessor processor) {
        processorMap.put(processor.bizInfo(), processor);
    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        for (SnapshotOperate snapshotOperate : snapshotOperates) {
            snapshotOperate.onSnapshotSave(writer, done);
        }
    }

    @Override
    public boolean onSnapshotLoad(SnapshotReader reader) {
        for (SnapshotOperate snapshotOperate : snapshotOperates) {
            if (!snapshotOperate.onSnapshotLoad(reader)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onLeaderStart(final long term) {
        super.onLeaderStart(term);
        this.leaderTerm.set(term);
        this.isLeader.set(true);
        NotifyManager.publishEvent(RaftEvent.class, RaftEvent.builder()
                .term(leaderTerm.get())

                // Means that he is a leader

                .leader(null)
                .raftClusterInfo(nodeManager.getAllNodes()
                        .stream()
                        .map(node -> node.getNodeId().getPeerId().toString())
                        .collect(Collectors.toList()))
                .build());
    }

    @Override
    public void onLeaderStop(final Status status) {
        super.onLeaderStop(status);
        this.isLeader.set(false);
    }

    @Override
    public void onStopFollowing(LeaderChangeContext ctx) {
        super.onStopFollowing(ctx);
        NotifyManager.publishEvent(RaftEvent.class, RaftEvent.builder()
                .term(leaderTerm.get())
                .leader(ctx.getLeaderId().toString())
                .raftClusterInfo(nodeManager.getAllNodes()
                        .stream()
                        .map(node -> node.getNodeId().getPeerId().toString())
                        .collect(Collectors.toList()))
                .build());
    }

    @Override
    public void onStartFollowing(LeaderChangeContext ctx) {
        super.onStartFollowing(ctx);
        NotifyManager.publishEvent(RaftEvent.class, RaftEvent.builder()
                .term(leaderTerm.get())
                .leader(ctx.getLeaderId().toString())
                .raftClusterInfo(nodeManager.getAllNodes()
                        .stream()
                        .map(node -> node.getNodeId().getPeerId().toString())
                        .collect(Collectors.toList()))
                .build());
    }

    @Override
    public void onError(RaftException e) {
        super.onError(e);
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    public Map<String, BizProcessor> getProcessorMap() {
        return processorMap;
    }
}
