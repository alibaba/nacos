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

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.NodeChangeEvent;
import com.alibaba.nacos.core.distributed.raft.exception.DuplicateRaftGroupException;
import com.alibaba.nacos.core.distributed.raft.exception.NoLeaderException;
import com.alibaba.nacos.core.distributed.raft.exception.RouteTableException;
import com.alibaba.nacos.core.distributed.raft.processor.NacosAsyncProcessor;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosure;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosureImpl;
import com.alibaba.nacos.core.distributed.raft.utils.JLog;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftUtils;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.core.distributed.raft.utils.RaftOptionsBuilder;
import com.alibaba.nacos.core.distributed.raft.utils.RetryRunner;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ConvertUtils;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.ThreadUtils;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.CliService;
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
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.rpc.impl.cli.BoltCliClientService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Slf4jReporter;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.util.CollectionUtils;

/**
 * JRaft server instance, away from Spring IOC management
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftServer implements MemberChangeListener {

    // Existential life cycle

    private RpcServer rpcServer;
    private BoltCliClientService cliClientService;
    private CliService cliService;
    private Map<String, RaftGroupTuple> multiRaftGroup = new ConcurrentHashMap<>();

    // Ordinary member variable

    private final MemberManager memberManager;
    private volatile boolean isStarted = false;
    private Configuration conf;

    private AsyncUserProcessor userProcessor;
    private NodeOptions nodeOptions;
    private Set<String> alreadyRegisterBiz = new HashSet<>();
    private Serializer serializer;
    private Collection<LogProcessor4CP> processors = Collections.synchronizedSet(new HashSet<>());

    private String selfIp;
    private int selfPort;

    private RaftConfig raftConfig;
    private PeerId localPeerId;
    private int failoverRetries;
    private int rpcRequestTimeoutMs;

    public JRaftServer(final MemberManager memberManager, final int failoverRetries) {
        this.memberManager = memberManager;
        this.failoverRetries = failoverRetries;
        this.conf = new Configuration();
    }

    public void setFailoverRetries(int failoverRetries) {
        this.failoverRetries = failoverRetries;
    }

    void init(RaftConfig config, Collection<LogProcessor4CP> processors) {
        this.raftConfig = config;
        this.serializer = SerializeFactory.getDefault();

        Loggers.RAFT.info("raft-config info : {}", config);

        RaftExecutor.init(config);

        final Member self = memberManager.self();
        selfIp = self.ip();
        selfPort = Integer.parseInt(String.valueOf(self.extendVal(MemberMetaDataConstants.RAFT_PORT)));
        localPeerId = new PeerId(selfIp, selfPort);
        nodeOptions = new NodeOptions();

        // Set the election timeout time. The default is 5 seconds.

        int electionTimeout = ConvertUtils.toInt(
                config.getVal(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS),
                RaftSysConstants.DEFAULT_ELECTION_TIMEOUT);

        nodeOptions.setElectionTimeoutMs(electionTimeout);

        initRpcRequestTimeoutMs();
        RaftOptions raftOptions = RaftOptionsBuilder.initRaftOptions(raftConfig);

        nodeOptions.setRaftOptions(raftOptions);

        this.cliClientService = new BoltCliClientService();
        cliClientService.init(new CliOptions());
        this.cliService = RaftServiceFactory.createAndInitCliService(new CliOptions());

        this.processors.addAll(processors);
    }

    synchronized void start() {

        if (!isStarted) {

            try {
                // init raft group node

                com.alipay.sofa.jraft.NodeManager raftNodeManager = com.alipay.sofa.jraft.NodeManager.getInstance();

                memberManager.allMembers().forEach(new Consumer<Member>() {
                    @Override
                    public void accept(Member member) {
                        final String ip = member.ip();
                        final int raftPort = Integer.parseInt(String.valueOf(member.extendVal(MemberMetaDataConstants.RAFT_PORT)));
                        PeerId peerId = new PeerId(member.ip(), raftPort);
                        conf.addPeer(peerId);
                        raftNodeManager.addAddress(peerId.getEndpoint());
                    }
                });

                nodeOptions.setInitialConf(conf);

                rpcServer = new RpcServer(selfPort, true, false);

                JRaftUtils.addRaftRequestProcessors(rpcServer, RaftExecutor.getRaftCoreExecutor(),
                        RaftExecutor.getRaftCliServiceExecutor());

                rpcServer.registerUserProcessor(new NacosAsyncProcessor(this, failoverRetries));

                if (!this.rpcServer.start()) {
                    Loggers.RAFT.error("Fail to init [RpcServer].");
                    throw new RuntimeException("Fail to init [RpcServer].");
                }

                // Initialize multi raft group service framework

                isStarted = true;

                createMultiRaftGroup(processors);
                NotifyCenter.registerSubscribe(this);
            } catch (Exception e) {
                Loggers.RAFT.error("raft protocol start failure, error : {}", e);
                throw new RuntimeException(e);
            }
        }
    }

    // Does not guarantee thread safety

    synchronized void createMultiRaftGroup(Collection<LogProcessor4CP> processors) {

        if (!this.isStarted) {
            this.processors.addAll(processors);
            return;
        }

        final String parentPath = Paths.get(ApplicationUtils.getNacosHome(), "protocol/raft").toString();

        for (LogProcessor4CP processor : processors) {

            final String groupName = processor.bizInfo();

            if (alreadyRegisterBiz.contains(groupName)) {
                throw new DuplicateRaftGroupException(groupName);
            }
            alreadyRegisterBiz.add(groupName);

            final String logUri = Paths.get(parentPath, groupName, "log").toString();
            final String snapshotUri = Paths.get(parentPath, groupName, "snapshot").toString();
            final String metaDataUri = Paths.get(parentPath, groupName, "meta-data").toString();

            // Initialize the raft file storage path for different services

            try {
                DiskUtils.forceMkdir(new File(logUri));
                DiskUtils.forceMkdir(new File(snapshotUri));
                DiskUtils.forceMkdir(new File(metaDataUri));
            } catch (Exception e) {
                Loggers.RAFT.error("Init Raft-File dir have some error : {}", e);
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

            int doSnapshotInterval = ConvertUtils.toInt(
                    raftConfig.getVal(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS),
                    RaftSysConstants.DEFAULT_RAFT_SNAPSHOT_INTERVAL_SECS);

            // If the business module does not implement a snapshot processor, cancel the snapshot

            doSnapshotInterval = CollectionUtils.isEmpty(processor.loadSnapshotOperate()) ? 0 : doSnapshotInterval;

            copy.setSnapshotIntervalSecs(doSnapshotInterval);

            Loggers.RAFT.info("create raft group : {}", groupName);

            RaftGroupService raftGroupService = new RaftGroupService(groupName, localPeerId, copy, rpcServer, true);

            // Because RpcServer has been started before, it is not allowed to start again here

            Node node = raftGroupService.start(false);

            machine.setNode(node);

            RouteTable.getInstance().updateConfiguration(groupName, configuration);

            // Turn on the leader auto refresh for this group

            Random random = new Random();

            long period = nodeOptions.getElectionTimeoutMs() + random.nextInt(5 * 1000);

            RaftExecutor.scheduleRaftMemberRefreshJob(() -> refreshRouteTable(groupName), period, period,
                    TimeUnit.MILLISECONDS);

            multiRaftGroup.put(groupName, new RaftGroupTuple(node, processor, raftGroupService));
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
                    commit(JRaftUtils.toJLog(NLog.builder()
                            .biz(biz)
                            .data(reqCtx)
                            .operation("JRAFT_READ_OPERATION")
                            .build(), JLog.JLogOperaton.READ_OPERATION), future, failoverRetries)
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

    public <T> CompletableFuture<T> commit(JLog data, final CompletableFuture<T> future, final int retryLeft) {
        Loggers.RAFT.debug("data requested this time : {}", data);
        final String biz = data.getBiz();
        final RaftGroupTuple tuple = findNodeByBiz(biz);
        if (tuple == null) {
            future.completeExceptionally(new IllegalArgumentException("No corresponding Raft Group found : " + biz));
            return future;
        }

        RetryRunner runner = () -> commit(data, future, retryLeft - 1);

        FailoverClosureImpl closure = new FailoverClosureImpl(future, retryLeft, runner);

        final Node node = tuple.node;
        if (node.isLeader()) {

            // The leader node directly applies this request

            applyOperation(node, data, closure);

        } else {

            // Forward to Leader for request processing

            invokeToLeader(node.getGroupId(), data, rpcRequestTimeoutMs, closure);

        }
        return future;
    }

    void addNode(Member member) {

        if (multiRaftGroup.isEmpty()) {
            Loggers.RAFT.warn("No RaftGroup information currently exists");
            return;
        }

        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {

            final Node node = entry.getValue().node;
            final String groupId = entry.getKey();
            final Configuration conf = RouteTable.getInstance().getConfiguration(groupId);
            final PeerId peerId = new PeerId(member.ip(), ConvertUtils.toInt(String.valueOf(member.extendVal(MemberMetaDataConstants.RAFT_PORT)), member.port() + 1000));

            final int retryCnt = failoverRetries > 1 ? failoverRetries : 3;

            RaftExecutor.executeByRaftCore(() -> {
                for (int i = 0; i < retryCnt; i ++) {

                    if (conf.contains(peerId)) {
                        return;
                    }

                    Status status = cliService.addPeer(groupId, conf, peerId);
                    if (status.isOk()) {
                        refreshRouteTable(groupId);
                        return;
                    } else {
                        Loggers.RAFT.error("Node join failed, groupId : {} peerId : {}, status : {}, Try again the {} time", groupId, peerId, status, i + 1);
                        ThreadUtils.sleep(500L);
                    }
                }
            });
        }
    }

    void removeNode(Member member) {

        if (multiRaftGroup.isEmpty()) {
            Loggers.RAFT.warn("No RaftGroup information currently exists");
            return;
        }

        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {

            final String groupId = entry.getKey();
            final Configuration conf = RouteTable.getInstance().getConfiguration(groupId);
            final PeerId peerId = new PeerId(member.ip(), ConvertUtils.toInt(String.valueOf(member.extendVal(MemberMetaDataConstants.RAFT_PORT)), member.port() + 1000));

            final int retryCnt = failoverRetries > 1 ? failoverRetries : 3;

            RaftExecutor.executeByRaftCore(() -> {
                for (int i = 0; i < retryCnt; i ++) {

                    if (!conf.contains(peerId)) {
                        return;
                    }

                    Status status = cliService.removePeer(groupId, conf, peerId);
                    if (status.isOk()) {
                        refreshRouteTable(groupId);
                        return;
                    } else {
                        Loggers.RAFT.error("Node remove failed, groupId : {}, peerId : {}, status : {}, Try again the {} time", groupId, peerId, status, i + 1);
                        ThreadUtils.sleep(500L);
                    }
                }
            });

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
        final Collection<Member> changeMembers = event.getChangeMembers();
        for (Member member : changeMembers) {
            if (Objects.equals("join", kind)) {
                addNode(member);
            } else {
                removeNode(member);
            }
        }
    }

    void shutdown() {

        Loggers.RAFT.warn("========= The raft protocol is about to close =========");

        NotifyCenter.deregisterSubscribe(this);

        for (Map.Entry<String, RaftGroupTuple> entry : multiRaftGroup.entrySet()) {
            final RaftGroupTuple tuple = entry.getValue();
            tuple.node.shutdown();
            tuple.raftGroupService.shutdown();
            tuple.regionMetricsReporter.close();
        }

        cliService.shutdown();
        cliClientService.shutdown();
        rpcServer.stop();

        Loggers.RAFT.warn("========= The raft protocol has been closed =========");
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
                    RestResult result = (RestResult) o;
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
            RouteTable instance = RouteTable.getInstance();
            Status status = instance.refreshConfiguration(this.cliClientService, group, 5000);
            if (status.isOk()) {
                Configuration conf = instance.getConfiguration(group);
                String leader = instance.selectLeader(group).getEndpoint().toString();
                List<String> groupMembers = JRaftUtils.toStrings(conf.getPeers());
                NotifyCenter.publishEvent(
                        RaftEvent.builder()
                                .groupId(group)
                                .leader(leader)
                                .raftClusterInfo(groupMembers)
                                .build());
            } else {
                Loggers.RAFT.error("Fail to refresh route configuration for group : {}, status is : {}", group, status);
            }
        } catch (Exception e) {
            Loggers.RAFT.error("Fail to refresh route configuration for group : {}, error is : {}", group, e);
        }
    }

    public RaftGroupTuple findNodeByBiz(final String biz) {
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

    private void initRpcRequestTimeoutMs() {
        rpcRequestTimeoutMs = ConvertUtils.toInt(
                raftConfig.getVal(
                        RaftSysConstants.RAFT_RPC_REQUEST_TIMEOUT_MS),
                RaftSysConstants.DEFAULT_RAFT_RPC_REQUEST_TIMEOUT_MS
        );
    }

    public static class RaftGroupTuple {

        private final LogProcessor processor;
        private final Node node;
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
