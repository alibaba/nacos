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

import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.core.cluster.NodeChangeEvent;
import com.alibaba.nacos.core.cluster.NodeChangeListener;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.raft.exception.DuplicateRaftGroupException;
import com.alibaba.nacos.core.distributed.raft.exception.NoLeaderException;
import com.alibaba.nacos.core.distributed.raft.exception.RouteTableException;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosure;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosureImpl;
import com.alibaba.nacos.core.distributed.raft.utils.JLog;
import com.alibaba.nacos.core.distributed.raft.utils.JLogUtils;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.core.distributed.raft.utils.RetryRunner;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.RaftServiceFactory;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.impl.cli.BoltCliClientService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Slf4jReporter;
import org.apache.commons.io.FileUtils;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private CliService cliService;
    private AsyncUserProcessor userProcessor;
    private NodeOptions nodeOptions;
    private Set<String> alreadyRegisterBiz = new HashSet<>();

    private Serializer serializer;

    private Map<String, RaftGroupTuple> multiRaftGroup = new HashMap<>();

    private Collection<LogProcessor> processors;

    private String selfIp;

    private int selfPort;

    private final NodeManager nodeManager;

    private RaftConfig raftConfig;

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private final Object monitor = new Object();

    public JRaftServer(final NodeManager nodeManager) {
        this.nodeManager = nodeManager;
        this.conf = new Configuration();

        NotifyCenter.registerSubscribe(this);
    }

    void init(RaftConfig config, Collection<LogProcessor> processors) {
        this.raftConfig = config;
        this.serializer = SerializeFactory.getDefault();

        RaftExecutor.init(config);

        final com.alibaba.nacos.core.cluster.Node self = nodeManager.self();
        selfIp = self.ip();
        selfPort = Integer.parseInt(String.valueOf(self.extendVal(RaftSysConstants.RAFT_PORT)));
        nodeOptions = new NodeOptions();

        // Set the election timeout time. The default is 5 seconds.

        int electionTimeout = Integer.parseInt(config.getValOfDefault(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS,
                String.valueOf(5000)));

        nodeOptions.setElectionTimeoutMs(electionTimeout);

        this.processors = processors;
    }

    void start() {

        if (isStarted.compareAndSet(false, true)) {

            try {
                // init raft group node

                com.alipay.sofa.jraft.NodeManager raftNodeManager = com.alipay.sofa.jraft.NodeManager.getInstance();

                nodeManager.allNodes().forEach(new Consumer<com.alibaba.nacos.core.cluster.Node>() {
                    @Override
                    public void accept(com.alibaba.nacos.core.cluster.Node node) {
                        final String ip = node.ip();
                        final int raftPort = Integer.parseInt(String.valueOf(node.extendVal(RaftSysConstants.RAFT_PORT)));
                        final String address = ip + ":" + raftPort;
                        PeerId peerId = JRaftUtils.getPeerId(address);
                        conf.addPeer(peerId);
                        raftNodeManager.addAddress(peerId.getEndpoint());
                    }
                });

                nodeOptions.setInitialConf(conf);

                rpcServer = new RpcServer(selfPort, true, false);

                rpcServer.registerUserProcessor(new NacosAsyncProcessor(this));

                RaftRpcServerFactory.addRaftRequestProcessors(rpcServer, RaftExecutor.getRaftCoreExecutor(),
                        RaftExecutor.getRaftCliServiceExecutor());

                if (!this.rpcServer.start()) {
                    Loggers.RAFT.error("Fail to init [RpcServer].");
                    throw new RuntimeException("Fail to init [RpcServer].");
                }

                // Initialize multi raft group service framework

                createMultiRaftGroup(processors);

                this.cliClientService = new BoltCliClientService();
                cliClientService.init(new CliOptions());

                this.cliService = RaftServiceFactory.createAndInitCliService(new CliOptions());
            } catch (Exception e) {
                Loggers.RAFT.error("raft protocol start failure, error : {}", ExceptionUtil.getAllExceptionMsg(e));
                throw new RuntimeException(e);
            }
        }
    }

    // Does not guarantee thread safety

    void createMultiRaftGroup(Collection<LogProcessor> processors) {
        synchronized (monitor) {

            final PeerId self = JRaftUtils.getPeerId(selfIp + ":" + selfPort);

            final String parentPath = Paths.get(SystemUtils.NACOS_HOME, "protocol/raft").toString();

            for (LogProcessor processor : processors) {

                final String _group = processor.bizInfo();

                if (alreadyRegisterBiz.contains(_group)) {
                    throw new DuplicateRaftGroupException(_group);
                }
                alreadyRegisterBiz.add(_group);

                final String logUri = Paths.get(parentPath, _group, "log").toString();
                final String snapshotUri = Paths.get(parentPath, _group, "snapshot").toString();
                final String metaDataUri = Paths.get(parentPath, _group, "meta-data").toString();

                // Initialize the raft file storage path for different services

                try {
                    FileUtils.forceMkdir(new File(logUri));
                    FileUtils.forceMkdir(new File(snapshotUri));
                    FileUtils.forceMkdir(new File(metaDataUri));
                } catch (Exception e) {
                    Loggers.RAFT.error("Init Raft-File dir have some error : {}", e.getMessage());
                    throw new RuntimeException(e);
                }

                // Ensure that each Raft Group has its own configuration and NodeOptions

                Configuration configuration = conf.copy();
                NodeOptions copy = nodeOptions.copy();

                NacosStateMachine machine = new NacosStateMachine(serializer, this, processor);

                copy.setLogUri(logUri);
                copy.setRaftMetaUri(metaDataUri);
                copy.setSnapshotUri(snapshotUri);
                copy.setFsm(machine);
                copy.setInitialConf(configuration);

                // Set snapshot interval, default 600 seconds

                int doSnapshotInterval = Integer.parseInt(raftConfig.getValOfDefault(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS,
                        String.valueOf(600)));

                // If the business module does not implement a snapshot processor, cancel the snapshot

                doSnapshotInterval = CollectionUtils.isEmpty(processor.loadSnapshotOperate()) ? 0 : doSnapshotInterval;

                copy.setSnapshotIntervalSecs(doSnapshotInterval);

                Loggers.RAFT.info("create raft group : {}", _group);

                RaftGroupService raftGroupService = new RaftGroupService(_group, self, copy, rpcServer, true);

                // Because RpcServer has been started before, it is not allowed to start again here

                Node node = raftGroupService.start(false);

                machine.setNode(node);

                RouteTable.getInstance().updateConfiguration(_group, configuration);

                // Turn on the leader auto refresh for this group

                Random random = new Random();

                long initialDelay = Duration.ofMinutes(3).toMillis() + random.nextInt(60 * 1000);
                long period = Duration.ofMinutes(3).toMillis() + random.nextInt(60 * 1000);

                RaftExecutor.scheduleRaftMemberRefreshJob(() -> refreshRouteTable(_group), initialDelay, period,
                        TimeUnit.MILLISECONDS);

                multiRaftGroup.put(_group, new RaftGroupTuple(node, processor, raftGroupService));
            }
        }
    }

    CompletableFuture<GetResponse<Object>> get(final GetRequest request, final int failoverRetries) {
        final String biz = request.getBiz();
        CompletableFuture<GetResponse<Object>> future = new CompletableFuture<>();
        final RaftGroupTuple tuple = findNodeByBiz(biz);
        if (Objects.isNull(tuple)) {
            future.completeExceptionally(new NoSuchElementException());
            return future;
        }
        final Node node = tuple.node;
        node.readIndex(request.getCtx(), new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] reqCtx) {
                if (status.isOk()) {
                    future.complete(tuple.processor.getData(request));
                } else {
                    // run raft read
                    commit(JLogUtils.toJLog(NLog.builder()
                            .biz(biz)
                            .data(reqCtx)
                            .operation("JRAFT_READ_OPERATION")
                            .build(), JLog.SYS_OPERATION), future, failoverRetries)
                            .whenComplete(new BiConsumer<Object, Throwable>() {
                                @Override
                                public void accept(Object result, Throwable throwable) {
                                    if (Objects.nonNull(throwable)) {
                                        future.completeExceptionally(throwable);
                                    } else {
                                        future.complete((GetResponse<Object>) result);
                                    }
                                }
                            });
                }
            }
        });
        return future;
    }

    <T> CompletableFuture<T> commit(JLog data, final CompletableFuture<T> future, final int retryLeft) {
        Loggers.RAFT.info("data requested this time : {}", data);
        final String biz = data.getBiz();
        final RaftGroupTuple tuple = findNodeByBiz(biz);
        if (tuple == null) {
            throw new IllegalArgumentException("No corresponding Raft Group found : " + biz);
        }

        RetryRunner runner = () -> commit(data, future, retryLeft - 1);

        FailoverClosureImpl closure = new FailoverClosureImpl(future, retryLeft, runner);

        final Node node = tuple.node;
        if (node.isLeader()) {

            // The leader node directly applies this request

            applyOperation(node, data, closure);

        } else {

            // Forward to Leader for request processing

            invokeToLeader(node.getGroupId(), data, 5000, closure);

        }
        return future;
    }

    void addNode(com.alibaba.nacos.core.cluster.Node node) {
        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {

            final String groupId = entry.getKey();
            Status status = cliService.addPeer(groupId, RouteTable.getInstance().getConfiguration(groupId),
                    PeerId.parsePeer(node.address()));

            if (status.isOk()) {
                refreshRouteTable(groupId);
            }
        }
    }

    void removeNode(com.alibaba.nacos.core.cluster.Node node) {
        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {

            final String groupId = entry.getKey();
            Status status = cliService.removePeer(groupId, RouteTable.getInstance().getConfiguration(groupId),
                    PeerId.parsePeer(node.address()));

            if (status.isOk()) {
                refreshRouteTable(groupId);
            }
        }
    }

    protected PeerId getLeader(final String raftGroupId) {
        final RouteTable routeTable = RouteTable.getInstance();
        final long deadline = System.currentTimeMillis() + 5000;
        final StringBuilder error = new StringBuilder();
        // A newly launched raft group may not have been successful in the election,
        // or in the 'leader-transfer' state, it needs to be re-tried
        Throwable lastCause = null;
        for (; ; ) {
            try {
                final Status st = routeTable.refreshLeader(this.cliClientService, raftGroupId, 2000);
                if (st.isOk()) {
                    break;
                }
                error.append(st.toString());
            } catch (final InterruptedException e) {
            } catch (final Throwable t) {
                lastCause = t;
                error.append(t.getMessage());
            }
            if (System.currentTimeMillis() < deadline) {
                Loggers.RAFT.debug("Fail to find leader, retry again, {}.", error);
                error.append(", ");
                try {
                    Thread.sleep(10);
                } catch (final InterruptedException e) {
                }
            } else {
                Loggers.RAFT.error("get Leader has error : {}", lastCause != null ?
                        new RouteTableException(error.toString(), lastCause)
                        : new RouteTableException(error.toString()));
                return null;
            }
        }
        return routeTable.selectLeader(raftGroupId);
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

        NotifyCenter.deregisterSubscribe(this);

        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {
            final RaftGroupTuple tuple = entry.getValue();
            tuple.raftGroupService.shutdown();
        }

        cliClientService.shutdown();
        rpcServer.stop();
    }

    private void applyOperation(Node node, JLog data, FailoverClosure closure) {
        final Task task = new Task();
        task.setDone(new NacosClosure(data, status -> {
            NacosClosure.NStatus nStatus = (NacosClosure.NStatus) status;
            if (Objects.nonNull(nStatus.getThrowable())) {
                closure.setThrowable(nStatus.getThrowable());
            } else {
                closure.setData(nStatus.getResult());
            }
            closure.run(nStatus);
        }));
        task.setData(ByteBuffer.wrap(serializer.serialize(data)));
        node.apply(task);
    }

    private void invokeToLeader(final String group, final Object request, final int timeoutMillis, FailoverClosure closure) {
        try {
            final String leaderIp = Optional.ofNullable(getLeader(group))
                    .orElseThrow(() -> new NoLeaderException(group))
                    .getEndpoint().toString();
            cliClientService.getRpcClient().invokeWithCallback(leaderIp, request, new InvokeCallback() {
                @Override
                public void onResponse(Object o) {
                    ResResult result = (ResResult) o;
                    closure.setData(result.getData());
                    closure.run(Status.OK());
                }

                @Override
                public void onException(Throwable e) {
                    closure.setThrowable(e);
                    closure.run(new Status(RaftError.UNKNOWN, e.getMessage()));
                }

                @Override
                public Executor getExecutor() {
                    return RaftExecutor.getRaftCliServiceExecutor();
                }
            }, timeoutMillis);
        } catch (Exception e) {
            closure.setThrowable(e);
            closure.run(new Status(RaftError.UNKNOWN, e.getMessage()));
        }
    }

    private void refreshRouteTable(String group) {
        int timeoutMs = 5000;
        try {
            RouteTable.getInstance().refreshConfiguration(this.cliClientService, group, 5000);
        } catch (InterruptedException | TimeoutException e) {
            Loggers.RAFT.error("Fail to refresh route configuration error is : {}", e.getMessage());
        }
    }

    RaftGroupTuple findNodeByBiz(final String biz) {
        RaftGroupTuple tuple = multiRaftGroup.get(biz);
        return tuple;
    }

    private Node findNodeByGroup(String group) {
        final RaftGroupTuple tuple = multiRaftGroup.get(group);
        if (Objects.nonNull(tuple)) {
            return tuple.node;
        }
        return null;
    }

    static class RaftGroupTuple {

        private final Node node;
        private final LogProcessor processor;
        private final RaftGroupService raftGroupService;
        private ScheduledReporter regionMetricsReporter;

        public RaftGroupTuple(Node node,
                              LogProcessor processor,
                              RaftGroupService raftGroupService) {
            this.node = node;
            this.processor = processor;
            this.raftGroupService = raftGroupService;

            final MetricRegistry metricRegistry = this.node.getNodeMetrics().getMetricRegistry();
            if (metricRegistry != null) {

                // auto start raft node metrics reporter

                regionMetricsReporter = Slf4jReporter.forRegistry(metricRegistry)
                        .prefixedWith("nacos_raft_[" + node.getGroupId() + "]")
                        .withLoggingLevel(Slf4jReporter.LoggingLevel.INFO)
                        .outputTo(Loggers.RAFT)
                        .scheduleOn(RaftExecutor.getRaftMemberRefreshExecutor())
                        .shutdownExecutorOnStop(RaftExecutor.getRaftMemberRefreshExecutor().isShutdown())
                        .build();
                regionMetricsReporter.start(30, TimeUnit.SECONDS);
            }

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
