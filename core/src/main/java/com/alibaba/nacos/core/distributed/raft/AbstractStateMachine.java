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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.snapshot.CallFinally;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperate;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractStateMachine extends StateMachineAdapter {

    private final AtomicLong leaderTerm = new AtomicLong(-1L);

    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    protected final JRaftServer server;

    protected final LogProcessor processor;

    private Collection<JSnapshotOperate> operates;

    private final String groupId;

    private NodeManager nodeManager = NodeManager.getInstance();

    public AbstractStateMachine(JRaftServer server, LogProcessor processor) {
        this.server = server;
        this.processor = processor;
        this.groupId = processor.bizInfo();

        List<SnapshotOperate> userOperates = processor.loadSnapshotOperate();

        this.operates = new ArrayList<>();

        for (SnapshotOperate item : userOperates) {
            operates.add(new JSnapshotOperate() {

                @Override
                public void onSnapshotSave(SnapshotWriter writer, Closure done) {
                    final Writer _w = new Writer();

                    // Do a layer of proxy operation to shield different Raft
                    // components from implementing snapshots

                    final BiConsumer<Boolean, Throwable> proxy = (result, t) -> {
                        _w.listFiles().forEach((file, meta) -> writer.addFile(file, buildMetadata(meta)));
                        final Status status = result ? Status.OK() : new Status(RaftError.EIO,
                                "Fail to compress snapshot at %s, error is %s", writer.getPath(),
                                t.getMessage());
                        done.run(status);
                    };
                    item.onSnapshotSave(_w, new CallFinally(proxy));
                }

                @Override
                public boolean onSnapshotLoad(SnapshotReader reader) {
                    final Map<String, LocalFileMeta> metaMap = new HashMap<>(8);
                    for (String fileName : reader.listFiles()) {
                        LocalFileMetaOutter.LocalFileMeta fileMeta = (LocalFileMetaOutter.LocalFileMeta)
                                reader.getFileMeta(fileName);
                        metaMap.put(fileName, new LocalFileMeta(JSON
                                .parseObject(fileMeta.getUserMeta().toByteArray(), Properties.class)));
                    }
                    final Reader _r = new Reader(reader.getPath(), metaMap);
                    return item.onSnapshotLoad(_r);
                }
            });
        }
    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        for (JSnapshotOperate operate : operates) {
            operate.onSnapshotSave(writer, done);
        }
    }

    @Override
    public boolean onSnapshotLoad(SnapshotReader reader) {
        for (JSnapshotOperate operate : operates) {
            if (!operate.onSnapshotLoad(reader)) {
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
                .groupId(groupId)
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
                .groupId(groupId)
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
                .groupId(groupId)
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
        processor.onError(e);
    }

    public boolean isLeader() {
        return isLeader.get();
    }

}
