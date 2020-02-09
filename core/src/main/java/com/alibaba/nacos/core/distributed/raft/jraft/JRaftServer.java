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

import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.core.cluster.NodeChangeEvent;
import com.alibaba.nacos.core.cluster.NodeChangeListener;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SerializeFactory;
import com.alibaba.nacos.core.utils.Serializer;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcResponseClosure;
import com.alipay.sofa.jraft.rpc.impl.cli.BoltCliClientService;
import com.alipay.sofa.jraft.util.BytesUtil;
import com.google.protobuf.Message;
import org.apache.commons.io.FileUtils;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * JRaft server instance, away from Spring IOC management
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftServer implements NodeChangeListener {

    private Configuration conf;
    private RpcServer rpcServer;
    private BoltCliClientService cliClientService;
    private AsyncUserProcessor userProcessor;
    private NodeOptions nodeOptions;
    private ScheduledExecutorService executorService;

    private Serializer serializer;

    private Map<String, RaftGroupTuple> multiRaftGroup = new HashMap<>();

    private Collection<LogProcessor> processors;

    private String selfIp;

    private int selfPort;

    private final NodeManager nodeManager;

    private RaftConfig raftConfig;

    public JRaftServer(final NodeManager nodeManager) {
        this.nodeManager = nodeManager;
        this.conf = new Configuration();

        NotifyManager.registerSubscribe(this);
    }

    void init(RaftConfig config, Collection<LogProcessor> processors) {
        this.raftConfig = config;
        this.serializer = SerializeFactory.getSerializerDefaultJson(config.getVal(RaftSysConstants.RAFT_SERIALIZER_TYPE));

        final com.alibaba.nacos.core.cluster.Node self = nodeManager.self();
        selfIp = self.ip();
        selfPort = Integer.parseInt(self.extendVal(RaftSysConstants.RAFT_PORT));
        nodeOptions = new NodeOptions();

        // Set the election timeout time. The default is 5 seconds.

        int electionTimeout = Integer.parseInt(config.getValOfDefault(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS,
                String.valueOf(5000)));

        nodeOptions.setElectionTimeoutMs(electionTimeout);

        this.processors = processors;
    }

    void start() {

        try {
            // init raft group node

            com.alipay.sofa.jraft.NodeManager raftNodeManager = com.alipay.sofa.jraft.NodeManager.getInstance();

            nodeManager.allNodes().forEach(new Consumer<com.alibaba.nacos.core.cluster.Node>() {
                @Override
                public void accept(com.alibaba.nacos.core.cluster.Node node) {
                    final String ip = node.ip();
                    final int raftPort = Integer.parseInt(node.extendVal(RaftSysConstants.RAFT_PORT));
                    final String address = ip + ":" + raftPort;
                    PeerId peerId = JRaftUtils.getPeerId(address);
                    conf.addPeer(peerId);
                    raftNodeManager.addAddress(peerId.getEndpoint());
                }
            });

            nodeOptions.setInitialConf(conf);

            rpcServer = new RpcServer(selfPort, true, true);

            rpcServer.registerUserProcessor(new NacosAsyncProcessor(this));

            RaftRpcServerFactory.addRaftRequestProcessors(rpcServer);

            // Initialize multi raft group service framework

            initMultiRaftGroup(processors, JRaftUtils.getPeerId(selfIp + ":" + selfPort), nodeOptions, rpcServer);

            this.cliClientService = new BoltCliClientService();
            cliClientService.init(new CliOptions());
        } catch (Exception e) {
            Loggers.RAFT.error("raft protocol start failure, error : {}", ExceptionUtil.getAllExceptionMsg(e));
            throw new RuntimeException(e);
        }
    }

    private void initMultiRaftGroup(Collection<LogProcessor> processors, PeerId self, NodeOptions options, RpcServer rpcServer) {
        final String parentPath = Paths.get(SystemUtils.NACOS_HOME, "protocol/raft").toString();

        this.executorService = ExecutorFactory.newScheduledExecutorService(JRaftServer.class.getCanonicalName(),
                processors.size(),
                new NameThreadFactory("com.alibaba.nacos.core.protocol.raft.node-refresh"));
        for (LogProcessor processor : processors) {

            final String _group = processor.bizInfo();

            final String logUri = Paths.get(parentPath, _group, "log").toString();
            final String snapshotUri = Paths.get(parentPath, _group, "snapshot").toString();
            final String metaDataUri = Paths.get(parentPath, _group, "meta-data").toString();

            try {
                FileUtils.forceMkdir(new File(logUri));
                FileUtils.forceMkdir(new File(snapshotUri));
                FileUtils.forceMkdir(new File(metaDataUri));
            } catch (Exception e) {
                Loggers.RAFT.error("Init Raft-File dir have some error : {}", e.getMessage());
                throw new RuntimeException(e);
            }

            NodeOptions copy = options.copy();
            copy.setLogUri(logUri);
            copy.setRaftMetaUri(metaDataUri);
            copy.setSnapshotUri(snapshotUri);
            copy.setFsm(new NacosStateMachine(serializer, this, processor));

            // Set snapshot interval, default 600 seconds

            int doSnapshotInterval = Integer.parseInt(raftConfig.getValOfDefault(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS,
                    String.valueOf(600)));

            // If the business module does not implement a snapshot processor, cancel the snapshot

            doSnapshotInterval = CollectionUtils.isEmpty(processor.loadSnapshotOperate()) ? 0 : doSnapshotInterval;

            copy.setSnapshotIntervalSecs(doSnapshotInterval);

            RaftGroupService raftGroupService = new RaftGroupService(_group, self, copy, rpcServer, true);

            Node node = raftGroupService.start();

            RouteTable.getInstance().updateConfiguration(_group, conf);

            // Turn on the leader auto refresh for this group

            executorService.scheduleAtFixedRate(() -> refreshRaftNode(_group), 3, 3,
                    TimeUnit.MINUTES);

            multiRaftGroup.put(_group, new RaftGroupTuple(node, processor, raftGroupService));
        }
    }

    CompletableFuture<Object> get(final String key) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        final RaftGroupTuple tuple = findNodeByLogKey(key);
        if (Objects.isNull(tuple)) {
            future.completeExceptionally(new NoSuchElementException());
        }
        final Node node = tuple.node;
        final GetRequest request = new GetRequest(key);
        node.readIndex(BytesUtil.EMPTY_BYTES, new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] reqCtx) {
                if (status.isOk()) {
                    future.complete(tuple.processor.getData(request));
                } else {
                    // run raft read
                    commit(JLogUtils.toJLog(NLog.builder()
                            .key(key)
                            .operation("JRAFT_READ_OPERATION")
                            .build(), JLog.SYS_OPERATION))
                            .whenComplete(new BiConsumer<Object, Throwable>() {
                                @Override
                                public void accept(Object result, Throwable throwable) {
                                    if (Objects.nonNull(throwable)) {
                                        future.completeExceptionally(throwable);
                                    } else {
                                        future.complete(result);
                                    }
                                }
                            });
                }
            }
        });
        return future;
    }

    <T> CompletableFuture<T> commit(JLog data) {
        final String key = data.getKey();
        final RaftGroupTuple tuple = findNodeByLogKey(data.getKey());
        if (tuple == null) {
            throw new IllegalArgumentException();
        }
        final Node node = tuple.node;
        final CompletableFuture<T> future = new CompletableFuture<>();
        if (node.isLeader()) {
            final Task task = new Task();
            task.setDone(new NacosClosure(data, status -> {
                NacosClosure.NStatus nStatus = (NacosClosure.NStatus) status;
                if (Objects.nonNull(nStatus.getThrowable())) {
                    future.completeExceptionally(nStatus.getThrowable());
                } else {
                    future.complete((T) nStatus.getResult());
                }
            }));
            task.setData(ByteBuffer.wrap(serializer.serialize(data)));
            node.apply(task);
        } else {
            try {

                // Forward to Leader for request processing

                cliClientService.getRpcClient().invokeWithCallback(
                        leaderNode(node.getGroupId()).getEndpoint().toString(), data, new InvokeCallback() {
                            @Override
                            public void onResponse(Object o) {
                                future.complete((T) o);
                            }

                            @Override
                            public void onException(Throwable e) {
                                future.completeExceptionally(e);
                            }

                            @Override
                            public Executor getExecutor() {
                                return null;
                            }
                        }, 5000);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    void addNode(com.alibaba.nacos.core.cluster.Node node) {
        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {
            final String groupId = entry.getKey();
            PeerId leader = leaderNode(groupId);
            final CliRequests.AddPeerRequest.Builder rb = CliRequests.AddPeerRequest
                    .newBuilder();
            rb.setGroupId(groupId);
            rb.setPeerId(PeerId.parsePeer(node.address()).toString());
            cliClientService.addPeer(leader.getEndpoint(), rb.build(),
                    new RpcResponseClosure<CliRequests.AddPeerResponse>() {
                        @Override
                        public void setResponse(CliRequests.AddPeerResponse resp) {
                        }

                        @Override
                        public void run(Status status) {
                        }
                    });
        }
    }

    void removeNode(com.alibaba.nacos.core.cluster.Node node) {
        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {
            final String groupId = entry.getKey();
            PeerId leader = leaderNode(groupId);
            final CliRequests.RemovePeerRequest.Builder rb = CliRequests.RemovePeerRequest
                    .newBuilder();
            rb.setGroupId(groupId);
            rb.setPeerId(PeerId.parsePeer(node.address()).toString());
            cliClientService.removePeer(leader.getEndpoint(), rb.build(),
                    new RpcResponseClosure<CliRequests.RemovePeerResponse>() {
                        @Override
                        public void setResponse(CliRequests.RemovePeerResponse resp) {
                        }

                        @Override
                        public void run(Status status) {
                        }
                    });
        }
    }

    PeerId leaderNode(String groupId) {
        final Node node = findNodeByGroup(groupId);
        if (node.getLeaderId() != null) {
            return node.getLeaderId();
        }
        final CliRequests.GetLeaderRequest.Builder rb = CliRequests.GetLeaderRequest
                .newBuilder();
        rb.setGroupId(groupId);
        rb.setPeerId(node.getNodeId().getPeerId().toString());
        try {
            Message result = cliClientService
                    .getLeader(node.getNodeId().getPeerId().getEndpoint(), rb.build(),
                            null)
                    .get(1000, TimeUnit.MILLISECONDS);
            if (result instanceof CliRequests.GetLeaderResponse) {
                CliRequests.GetLeaderResponse resp = (CliRequests.GetLeaderResponse) result;
                return JRaftUtils.getPeerId(resp.getLeaderId());
            }
        } catch (Exception e) {
            Loggers.RAFT.error("Get leader node has error : {}", e.getMessage());
        }
        return PeerId.parsePeer(nodeManager.self().address());
    }

    @Override
    public void onEvent(NodeChangeEvent event) {
        final String kind = event.getKind();
        final Collection<com.alibaba.nacos.core.cluster.Node> changeNodes = event.getChangeNodes();
        for (com.alibaba.nacos.core.cluster.Node node : changeNodes) {
            if (Objects.equals("join", kind)) {
                addNode(node);
            } else {
                removeNode(node);
            }
        }
    }

    void shutdown() {

        NotifyManager.deregisterSubscribe(this);

        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {
            final RaftGroupTuple tuple = entry.getValue();
            tuple.raftGroupService.shutdown();
        }

        cliClientService.shutdown();
        rpcServer.stop();
    }

    private void refreshRaftNode(String group) {
        int timeoutMs = 5000;
        try {
            if (!RouteTable.getInstance()
                    .refreshLeader(cliClientService, group, timeoutMs).isOk()) {
                Loggers.RAFT.warn("refresh raft node info failed");
            }
        } catch (InterruptedException | TimeoutException e) {
            Loggers.RAFT.error("refresh raft node info failed, error is : {}", e.getMessage());
        }
    }

    RaftGroupTuple findNodeByLogKey(final String key) {
        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {
            final RaftGroupTuple tuple = entry.getValue();
            if (tuple.processor.interest(key)) {
                return tuple;
            }
        }
        return null;
    }

    private Node findNodeByGroup(String group) {
        final RaftGroupTuple tuple = multiRaftGroup.get(group);
        if (Objects.nonNull(tuple)) {
            return tuple.node;
        }
        return null;
    }

    public RpcServer getRpcServer() {
        return rpcServer;
    }

    public BoltCliClientService getCliClientService() {
        return cliClientService;
    }

    static class RaftGroupTuple {

        private final Node node;
        private final LogProcessor processor;
        private final RaftGroupService raftGroupService;

        public RaftGroupTuple(Node node, LogProcessor processor, RaftGroupService raftGroupService) {
            this.node = node;
            this.processor = processor;
            this.raftGroupService = raftGroupService;
        }

        public Node getNode() {
            return node;
        }

        public LogProcessor getProcessor() {
            return processor;
        }

        public RaftGroupService getRaftGroupService() {
            return raftGroupService;
        }
    }

}
