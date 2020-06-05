/*
 *
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
 *
 */

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public enum JRaftOps {

	TRANSFER_LEADER("transferLeader") {
		@Override
		public RestResult<String> execute(CliService cliService, String groupId,
				Node node, Map<String, String> args) {
			final Configuration conf = node.getOptions().getInitialConf();
			final PeerId leader = PeerId
					.parsePeer(args.get(JRaftConstants.COMMAND_VALUE));
			Status status = cliService.transferLeader(groupId, conf, leader);
			if (status.isOk()) {
				return RestResultUtils.success();
			}
			return RestResultUtils.failed(status.getErrorMsg());
		}
	},

	RESET_RAFT_CLUSTER("restRaftCluster") {

		@Override
		public RestResult<String> execute(CliService cliService, String groupId,
				Node node, Map<String, String> args) {
			final Configuration conf = node.getOptions().getInitialConf();
			final String peerIds = args.get(JRaftConstants.COMMAND_VALUE);
			Configuration newConf = JRaftUtils.getConfiguration(peerIds);
			Status status = cliService.changePeers(groupId, conf, newConf);
			if (status.isOk()) {
				return RestResultUtils.success();
			}
			return RestResultUtils.failed(status.getErrorMsg());
		}
	},

	DO_SNAPSHOT("doSnapshot") {
		@Override
		public RestResult<String> execute(CliService cliService, String groupId,
				Node node, Map<String, String> args) {
			final Configuration conf = node.getOptions().getInitialConf();
			final PeerId peerId = PeerId
					.parsePeer(args.get(JRaftConstants.COMMAND_VALUE));
			Status status = cliService.snapshot(groupId, peerId);
			if (status.isOk()) {
				return RestResultUtils.success();
			}
			return RestResultUtils.failed(status.getErrorMsg());
		}
	},

	REMOVE_PEER("removePeer") {
		@Override
		public RestResult<String> execute(CliService cliService, String groupId,
				Node node, Map<String, String> args) {
			final Configuration conf = node.getOptions().getInitialConf();

			List<PeerId> peerIds = cliService.getPeers(groupId, conf);

			final PeerId waitRemove = PeerId
					.parsePeer(args.get(JRaftConstants.COMMAND_VALUE));

			if (!peerIds.contains(waitRemove)) {
				return RestResultUtils.success();
			}

			Status status = cliService.removePeer(groupId, conf, waitRemove);
			if (status.isOk()) {
				return RestResultUtils.success();
			}
			return RestResultUtils.failed(status.getErrorMsg());
		}
	},

	REMOVE_PEERS("removePeers") {
		@Override
		public RestResult<String> execute(CliService cliService, String groupId,
				Node node, Map<String, String> args) {
			final Configuration conf = node.getOptions().getInitialConf();
			final String peers = args.get(JRaftConstants.COMMAND_VALUE);
			for (String s : peers.split(",")) {

				List<PeerId> peerIds = cliService.getPeers(groupId, conf);
				final PeerId waitRemove = PeerId.parsePeer(s);

				if (!peerIds.contains(waitRemove)) {
					continue;
				}

				Status status = cliService.removePeer(groupId, conf, waitRemove);
				if (!status.isOk()) {
					return RestResultUtils.failed(status.getErrorMsg());
				}
			}
			return RestResultUtils.success();
		}
	},

	CHANGE_PEERS("changePeers") {
		@Override
		public RestResult<String> execute(CliService cliService, String groupId,
				Node node, Map<String, String> args) {
			final Configuration conf = node.getOptions().getInitialConf();
			final Configuration newConf = new Configuration();
			String peers = args.get(JRaftConstants.COMMAND_VALUE);
			for (String peer : peers.split(",")) {
				newConf.addPeer(PeerId.parsePeer(peer.trim()));
			}

			if (Objects.equals(conf, newConf)) {
				return RestResultUtils.success();
			}

			Status status = cliService.changePeers(groupId, conf, newConf);
			if (status.isOk()) {
				return RestResultUtils.success();
			}
			return RestResultUtils.failed(status.getErrorMsg());
		}
	}

	;

	private String name;

	JRaftOps(String name) {
		this.name = name;
	}

	public RestResult<String> execute(CliService cliService, String groupId, Node node,
			Map<String, String> args) {
		return RestResultUtils.success();
	}

	public static JRaftOps sourceOf(String command) {
		for (JRaftOps enums : JRaftOps.values()) {
			if (Objects.equals(command, enums.name)) {
				return enums;
			}
		}
		return null;
	}
}
