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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.core.distributed.raft.processor.NAddPeerRequestProcessor;
import com.alibaba.nacos.core.distributed.raft.processor.NRemovePeerRequestProcessor;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.impl.PingRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.AddLearnersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.ChangePeersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.GetLeaderRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.GetPeersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.RemoveLearnersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.ResetLearnersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.ResetPeerRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.SnapshotRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.TransferLeaderRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.AppendEntriesRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.GetFileRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.InstallSnapshotRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.ReadIndexRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.RequestVoteRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.TimeoutNowRequestProcessor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftUtils {

    public static final Log injectExtendInfo(Log log, final String operate) {
        Log gLog = Log.newBuilder(log)
                .putExtendInfo(JRaftConstants.JRAFT_EXTEND_INFO_KEY, operate)
                .build();
        return gLog;
    }

    public static List<String> toStrings(List<PeerId> peerIds) {
        return peerIds.stream().map(peerId -> peerId.getEndpoint().toString())
                .collect(Collectors.toList());
    }

    public static void addRaftRequestProcessors(final RpcServer rpcServer, final Executor raftExecutor,
            final Executor cliExecutor) {
        // raft core processors
        final AppendEntriesRequestProcessor appendEntriesRequestProcessor = new AppendEntriesRequestProcessor(
                raftExecutor);
        rpcServer.addConnectionEventProcessor(ConnectionEventType.CLOSE, appendEntriesRequestProcessor);
        rpcServer.registerUserProcessor(appendEntriesRequestProcessor);
        rpcServer.registerUserProcessor(new GetFileRequestProcessor(raftExecutor));
        rpcServer.registerUserProcessor(new InstallSnapshotRequestProcessor(raftExecutor));
        rpcServer.registerUserProcessor(new RequestVoteRequestProcessor(raftExecutor));
        rpcServer.registerUserProcessor(new PingRequestProcessor());
        rpcServer.registerUserProcessor(new TimeoutNowRequestProcessor(raftExecutor));
        rpcServer.registerUserProcessor(new ReadIndexRequestProcessor(raftExecutor));
        // raft cli service
        rpcServer.registerUserProcessor(new NAddPeerRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new NRemovePeerRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new ResetPeerRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new ChangePeersRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new GetLeaderRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new SnapshotRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new TransferLeaderRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new GetPeersRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new AddLearnersRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new RemoveLearnersRequestProcessor(cliExecutor));
        rpcServer.registerUserProcessor(new ResetLearnersRequestProcessor(cliExecutor));
    }

}
