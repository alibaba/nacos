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
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.Constants;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.AbstractConsistencyProtocol;
import com.alibaba.nacos.core.distributed.raft.exception.NoSuchRaftGroupException;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Node;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A concrete implementation of CP protocol: JRaft
 *
 * <pre>
 *                                           ┌──────────────────────┐
 *            ┌──────────────────────┐       │                      ▼
 *            │   ProtocolManager    │       │        ┌───────────────────────────┐
 *            └──────────────────────┘       │        │for p in [LogProcessor4CP] │
 *                        │                  │        └───────────────────────────┘
 *                        ▼                  │                      │
 *      ┌──────────────────────────────────┐ │                      ▼
 *      │    discovery LogProcessor4CP     │ │             ┌─────────────────┐
 *      └──────────────────────────────────┘ │             │  get p.group()  │
 *                        │                  │             └─────────────────┘
 *                        ▼                  │                      │
 *                 ┌─────────────┐           │                      │
 *                 │ RaftConfig  │           │                      ▼
 *                 └─────────────┘           │      ┌──────────────────────────────┐
 *                        │                  │      │  create raft group service   │
 *                        ▼                  │      └──────────────────────────────┘
 *              ┌──────────────────┐         │
 *              │  JRaftProtocol   │         │
 *              └──────────────────┘         │
 *                        │                  │
 *                     init()                │
 *                        │                  │
 *                        ▼                  │
 *               ┌─────────────────┐         │
 *               │   JRaftServer   │         │
 *               └─────────────────┘         │
 *                        │                  │
 *                        │                  │
 *                        ▼                  │
 *             ┌────────────────────┐        │
 *             │JRaftServer.start() │        │
 *             └────────────────────┘        │
 *                        │                  │
 *                        └──────────────────┘
 * </pre>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JRaftProtocol
		extends AbstractConsistencyProtocol<RaftConfig, LogProcessor4CP>
		implements CPProtocol<RaftConfig, LogProcessor4CP> {

	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private final AtomicBoolean shutdowned = new AtomicBoolean(false);
	private RaftConfig raftConfig;
	private JRaftServer raftServer;
	private JRaftMaintainService jRaftMaintainService;
	private Node raftNode;
	private ServerMemberManager memberManager;
	private String selfAddress = InetUtils.getSelfIp();
	private final Serializer serializer = SerializeFactory.getDefault();

	public JRaftProtocol(ServerMemberManager memberManager) throws Exception {
		this.memberManager = memberManager;
		this.raftServer = new JRaftServer();
		this.jRaftMaintainService = new JRaftMaintainService(raftServer);
	}

	@Override
	public void init(RaftConfig config) {
		if (initialized.compareAndSet(false, true)) {
			this.raftConfig = config;
			this.selfAddress = memberManager.getSelf().getAddress();
			NotifyCenter.registerToSharePublisher(RaftEvent.class);
			this.raftServer.init(this.raftConfig);
			this.raftServer.start();

			// There is only one consumer to ensure that the internal consumption
			// is sequential and there is no concurrent competition
			NotifyCenter.registerSubscribe(new Subscribe<RaftEvent>() {
				@Override
				public void onEvent(RaftEvent event) {
					Loggers.RAFT.info("This Raft event changes : {}", event);
					final String groupId = event.getGroupId();
					Map<String, Map<String, Object>> value = new HashMap<>();
					Map<String, Object> properties = new HashMap<>();
					final String leader = event.getLeader();
					final Long term = event.getTerm();
					final List<String> raftClusterInfo = event.getRaftClusterInfo();

					// Leader information needs to be selectively updated. If it is valid data,
					// the information in the protocol metadata is updated.
					if (StringUtils.isNotBlank(leader)) {
						properties.put(Constants.LEADER_META_DATA, leader);
					}
					if (Objects.nonNull(term)) {
						properties.put(Constants.TERM_META_DATA, term);
					}
					if (CollectionUtils.isNotEmpty(raftClusterInfo)) {
						properties.put(Constants.RAFT_GROUP_MEMBER, raftClusterInfo);
					}
					value.put(groupId, properties);
					metaData.load(value);

					// The metadata information is injected into the metadata information of the node
					injectProtocolMetaData(metaData);
				}

				@Override
				public Class<? extends Event> subscribeType() {
					return RaftEvent.class;
				}

			});
		}
	}

	@Override
	public void addLogProcessors(Collection<LogProcessor4CP> processors) {
		raftServer.createMultiRaftGroup(processors);
	}

	@Override
	public Response getData(GetRequest request) throws Exception {
		return raftServer.get(request);
	}

	@Override
	public Response submit(Log data) throws Exception {
		CompletableFuture<Response> future = submitAsync(data);
		// Here you wait for 10 seconds, as long as possible, for the request to complete
		return future.get(10_000L, TimeUnit.MILLISECONDS);
	}

	@Override
	public CompletableFuture<Response> submitAsync(Log data) {
		CompletableFuture<Response> future = new CompletableFuture<>();
		raftServer.commit(data.getGroup(), data, future);
		return future;
	}

	@Override
	public void memberChange(Set<String> addresses) {
		for (int i = 0; i < 5; i ++) {
			if (this.raftServer.peerChange(jRaftMaintainService, addresses)) {
				return;
			}
			ThreadUtils.sleep(100L);
		}
		Loggers.RAFT.warn("peer removal failed");
	}

	@Override
	public void shutdown() {
		if (initialized.get() && shutdowned.compareAndSet(false, true)) {
			raftServer.shutdown();
		}
	}

	@Override
	public RestResult<String> execute(Map<String, String> args) {
		return jRaftMaintainService.execute(args);
	}

	private void injectProtocolMetaData(ProtocolMetaData metaData) {
		Member member = memberManager.getSelf();
		member.setExtendVal("raft_meta_data", metaData);
		memberManager.update(member);
	}

	@Override
	public boolean isLeader(String group) throws Exception {
		Node node = raftServer.findNodeByGroup(group);
		if (node == null) {
			throw new NoSuchRaftGroupException(group);
		}
		return node.isLeader();
	}
}
