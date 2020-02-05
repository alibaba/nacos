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

import com.alibaba.nacos.core.cluster.NodeChangeEvent;
import com.alibaba.nacos.core.cluster.NodeChangeListener;
import com.alibaba.nacos.core.cluster.ServerNodeManager;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcResponseClosure;
import com.alipay.sofa.jraft.rpc.impl.cli.BoltCliClientService;
import com.google.protobuf.Message;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * JRaft server instance, away from Spring IOC management
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftServer implements NodeChangeListener {

    private String raftGroupId = "NACOS";

    private RaftGroupService raftGroupService;
    private Node node;
    private Configuration conf;
    private RpcServer rpcServer;
    private BoltCliClientService cliClientService;
    private NacosStateMachine machine;
    private AsyncUserProcessor processor;
    private NodeOptions nodeOptions;
    private ScheduledExecutorService executorService;

    private String selfIp;

    private int selfPort;

    private final ServerNodeManager nodeManager;

    public JRaftServer(final ServerNodeManager nodeManager, final NacosStateMachine machine, final AsyncUserProcessor processor) {
        this.nodeManager = nodeManager;
        this.machine = machine;
        this.processor = processor;
        this.conf = new Configuration();
        this.executorService = ExecutorFactory.newSingleScheduledExecutorService(JRaftServer.class.getCanonicalName(),
                new NameThreadFactory("com.alibaba.nacos.core.protocol.raft.node-refresh"));

        NotifyManager.registerSubscribe(this);
    }

    void init(RaftConfig config) {
        String parentPath = Paths.get(SystemUtils.NACOS_HOME, "protocol/raft").toString();
        try {
            FileUtils.forceMkdir(new File(parentPath));
        } catch (Exception e) {
            Loggers.RAFT.error("Init Raft-File dir have some error : {}", e.getMessage());
            throw new RuntimeException(e);
        }
        final String logUri = Paths.get(parentPath, "log").toString();
        final String snapshotUri = Paths.get(parentPath, "snapshot").toString();
        final String metaDataUri = Paths.get(parentPath, "meta-data").toString();

        final com.alibaba.nacos.core.cluster.Node self = nodeManager.self();
        selfIp = self.ip();
        selfPort = Integer.parseInt(self.extendVal(RaftSysConstants.RAFT_PORT));
        nodeOptions = new NodeOptions();

        // Set the election timeout time. The default is 5 seconds.

        int electionTimeout = Integer.parseInt(config.getValOfDefault(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS,
                String.valueOf(5000)));

        nodeOptions.setElectionTimeoutMs(electionTimeout);

        // Set snapshot interval, default 600 seconds

        int doSnapshotInterval = Integer.parseInt(config.getValOfDefault(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS,
                String.valueOf(600)));

        nodeOptions.setSnapshotIntervalSecs(doSnapshotInterval);

        nodeOptions.setLogUri(logUri);
        nodeOptions.setRaftMetaUri(metaDataUri);
        nodeOptions.setSnapshotUri(snapshotUri);
    }

    void start() {

        try {

            // init raft group node

            NodeManager raftNodeManager = NodeManager.getInstance();

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

            nodeOptions.setFsm(this.machine);

            rpcServer = new RpcServer(selfPort, true, true);

            rpcServer.registerUserProcessor(processor);

            RaftRpcServerFactory.addRaftRequestProcessors(rpcServer);

            // Initialize the raft group service framework

            this.raftGroupService = new RaftGroupService(raftGroupId,
                    JRaftUtils.getPeerId(selfIp + ":" + selfPort), nodeOptions, rpcServer);

            this.node = this.raftGroupService.start();

            RouteTable.getInstance().updateConfiguration(raftGroupId, conf);

            this.cliClientService = new BoltCliClientService();
            cliClientService.init(new CliOptions());

            executorService.scheduleAtFixedRate(this::refreshRaftNode, 3, 3,
                    TimeUnit.MINUTES);
        } catch (Exception e) {
            Loggers.RAFT.error("raft protocol start failure, error : {}", ExceptionUtil.getAllExceptionMsg(e));
            throw new RuntimeException(e);
        }
    }

    void addNode(com.alibaba.nacos.core.cluster.Node node) {
        PeerId leader = leaderNode();
        final CliRequests.AddPeerRequest.Builder rb = CliRequests.AddPeerRequest
                .newBuilder();
        rb.setGroupId(raftGroupId);
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

    void removeNode(com.alibaba.nacos.core.cluster.Node node) {
        PeerId leader = leaderNode();
        final CliRequests.RemovePeerRequest.Builder rb = CliRequests.RemovePeerRequest
                .newBuilder();
        rb.setGroupId(raftGroupId);
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

    PeerId leaderNode() {
        if (node.getLeaderId() != null) {
            return node.getLeaderId();
        }
        final CliRequests.GetLeaderRequest.Builder rb = CliRequests.GetLeaderRequest
                .newBuilder();
        rb.setGroupId(raftGroupId);
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
        }
        catch (Exception e) {
            Loggers.RAFT.error("Get leader node has error : {}", e.getMessage());
        }
        return PeerId.parsePeer(nodeManager.self().address());
    }

    String leaderIp() {
        PeerId leader = leaderNode();
        return leader.getIp() + ":" + leader.getPort();
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

        raftGroupService.shutdown();
        cliClientService.shutdown();
        rpcServer.stop();
    }

    private void refreshRaftNode() {
        int timeoutMs = 5000;
        try {
            if (!RouteTable.getInstance()
                    .refreshLeader(cliClientService, raftGroupId, timeoutMs).isOk()) {
                Loggers.RAFT.warn("refresh raft node info failed");
            }
        }
        catch (InterruptedException | TimeoutException e) {
            Loggers.RAFT.error("refresh raft node info failed, error is : {}", e.getMessage());
        }
    }

    public Node getNode() {
        return node;
    }

    public RaftGroupService getRaftGroupService() {
        return raftGroupService;
    }

    public RpcServer getRpcServer() {
        return rpcServer;
    }

    public BoltCliClientService getCliClientService() {
        return cliClientService;
    }
}
