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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;

import java.util.Map;

/**
 * JRaft operations interface
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JRaftOps {

	private final JRaftServer raftServer;

	public JRaftOps(JRaftServer raftServer) {
		this.raftServer = raftServer;
	}

	public RestResult<String> execute(String[] args) {
		return RestResultUtils.failed("not support yet");
	}

	public RestResult<String> execute(Map<String, String> args) {
		final CliService cliService = raftServer.getCliService();
		if (args.containsKey(JRaftConstants.GROUP_ID)) {
			final String groupId = args.get(JRaftConstants.GROUP_ID);
			final Node node = raftServer.findNodeByGroup(groupId);
			return single(cliService, groupId, node, args);
		}
		Map<String, JRaftServer.RaftGroupTuple> tupleMap = raftServer.getMultiRaftGroup();
		for (Map.Entry<String, JRaftServer.RaftGroupTuple> entry : tupleMap.entrySet()) {
			final String group = entry.getKey();
			final Node node = entry.getValue().getNode();
			RestResult<String> result = single(cliService, group, node, args);
			if (!result.ok()) {
				return result;
			}
		}
		return RestResultUtils.success();
	}

	private RestResult<String> single(CliService cliService, String groupId, Node node,
			Map<String, String> args) {
		try {
			if (node == null) {
				return RestResultUtils.failed("not this raft group : " + groupId);
			}

			final Configuration conf = node.getOptions().getInitialConf();
			if (args.containsKey(JRaftConstants.TRANSFER_LEADER)) {
				final PeerId leader = PeerId
						.parsePeer(args.get(JRaftConstants.TRANSFER_LEADER));
				Status status = cliService.transferLeader(groupId, conf, leader);
				if (status.isOk()) {
					return RestResultUtils.success();
				}
				return RestResultUtils.failed(status.getErrorMsg());
			}
			if (args.containsKey(JRaftConstants.RESET_RAFT_CLUSTER)) {
				final String peerIds = args.get(JRaftConstants.RESET_RAFT_CLUSTER);
				Configuration newConf = JRaftUtils.getConfiguration(peerIds);
				Status status = cliService.changePeers(groupId, conf, newConf);
				if (status.isOk()) {
					return RestResultUtils.success();
				}
				return RestResultUtils.failed(status.getErrorMsg());
			}
			if (args.containsKey(JRaftConstants.DO_SNAPSHOT)) {
				final PeerId peerId = PeerId
						.parsePeer(args.get(JRaftConstants.DO_SNAPSHOT));
				Status status = cliService.snapshot(groupId, peerId);
				if (status.isOk()) {
					return RestResultUtils.success();
				}
				return RestResultUtils.failed(status.getErrorMsg());
			}
			return RestResultUtils.failed();
		}
		catch (Throwable ex) {
			return RestResultUtils.failed(ex.getMessage());
		}
	}

}
