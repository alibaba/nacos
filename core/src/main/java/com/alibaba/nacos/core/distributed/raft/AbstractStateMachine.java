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
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.apache.commons.lang3.BooleanUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractStateMachine extends StateMachineAdapter {

    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    protected final JRaftServer server;

    protected final LogProcessor processor;

    private Collection<JSnapshotOperate> operates;

    private final String groupId;

    private Node node;

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
                    final Writer _w = new Writer(writer.getPath());

                    // Do a layer of proxy operation to shield different Raft
                    // components from implementing snapshots

                    final BiConsumer<Boolean, Throwable> proxy = (result, t) -> {
                        boolean[] results = new  boolean[_w.listFiles().size()];
                        int[] index = new int[]{ 0 };
                        _w.listFiles().forEach((file, meta) -> results[index[0] ++] = writer.addFile(file, buildMetadata(meta)));
                        final Status status = result && BooleanUtils.and(results) ? Status.OK() : new Status(RaftError.EIO,
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

    public void setNode(Node node) {
        this.node = node;
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
        this.isLeader.set(true);
        NotifyManager.publishEvent(RaftEvent.class, RaftEvent.builder()
                .groupId(groupId)
                .leader(node.getNodeId().getPeerId().getEndpoint().toString())
                .term(term)
                .raftClusterInfo(allPeers())
                .build());
    }

    @Override
    public void onLeaderStop(final Status status) {
        super.onLeaderStop(status);
        this.isLeader.set(false);
    }

    @Override
    public void onStopFollowing(LeaderChangeContext ctx) {
        NotifyManager.publishEvent(RaftEvent.class, RaftEvent.builder()
                .groupId(groupId)
                .leader(ctx.getLeaderId().getEndpoint().toString())
                .term(ctx.getTerm())
                .raftClusterInfo(allPeers())
                .build());
    }

    @Override
    public void onStartFollowing(LeaderChangeContext ctx) {
        NotifyManager.publishEvent(RaftEvent.class, RaftEvent.builder()
                .groupId(groupId)
                .leader(ctx.getLeaderId().getEndpoint().toString())
                .term(ctx.getTerm())
                .raftClusterInfo(allPeers())
                .build());
    }

    @Override
    public void onError(RaftException e) {
        processor.onError(e);
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    private List<String> allPeers() {
        if (node == null) {
            return Collections.emptyList();
        }
        return RouteTable.getInstance()
                .getConfiguration(node.getGroupId())
                .getPeers()
                .stream().map(peerId -> peerId.getEndpoint().toString())
                .collect(Collectors.toList());
    }

}
