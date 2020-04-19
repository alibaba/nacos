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

package com.alibaba.nacos.core.cluster.lookup;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.TimerContext;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <pre>
 *     ┌─────────────────────────────────────────────────────────────────────────┐
 *     │                                            ┌─────────────────────────┐  │
 *     │                                            │        Member A         │  │
 *     │                                            │   [ip1.port,ip2.port]   │  │
 *     │       ┌───────────────────────┐            │                         │  │
 *     │       │ DiscoveryMemberLookup │            └─────────────────────────┘  │
 *     │       └───────────────────────┘                                         │
 *     │                   │                                                     │
 *     │                   │                                     ┌───────────────┼────────────────[ip1:port,ip2:port,ip3:port]───────────────────────┐
 *     │                   │                                     │               │                                                                   │
 *     │                   ▼                                     ▼               │                                                                   │
 *     │  ┌────────────────────────────────┐         ┌──────────────────────┐    │                                                                   │
 *     │  │     read init members from     │ ┌──────▶│  MemberListSyncTask  │╲   │                                                                   │
 *     │  │        cluster.conf or         │ │       └──────────────────────┘ ╲  │                                                                   │
 *     │  └────────────────────────────────┘ │                                 ╲ │                                                                   │
 *     │                   │                 │                                  ╲│                                                                   │
 *     │                   │                 │                                   ╳                                                                   │
 *     │                   │                 │                                   │╲──────Gets a list of cluster members ────╲                        │
 *     │                   │                 │                                   │               known to node B             ╲                       │
 *     │                   │                 │                                   │                                            ╲   ┌────────────────────────────────────┐
 *     │                   ▼                 │                                   │                                             ╲  │                                    │
 *     │        ┌─────────────────────┐      │       ┌─────────────────────────┐ │    Broadcast the node that the local node    ╲ │              Member B              │
 *     │        │  init gossip task   │──────┼──────▶│ MemberDeadBroadcastTask │─┼─────────────considers to be DOWN──────────────▶│    [ip1:port,ip2.port,ip3.port]    │
 *     │        └─────────────────────┘              └─────────────────────────┘ │                                                │   {adweight:"",site:"",state:""}   │
 *     │                   │                                                     │                                                │                                    │
 *     │                   │                                                     │                                                └────────────────────────────────────┘
 *     │                   │                                                     │
 *     │                   ▼                                                     │
 *     │   ┌──────────────────────────────┐         ┌────────────────────────┐   │
 *     │   │register member shutdown task │────────▶│   MemberShutdownTask   │   │
 *     │   └──────────────────────────────┘         └────────────────────────┘   │
 *     └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <ul>
 *     <li>{@link MemberListSyncTask} : Cluster node list synchronization tasks</li>
 *     <li>{@link MemberDeadBroadcastTask} : Broadcast a node that it thinks is already in a DOWN state machine</li>
 *     <li>{@link MemberShutdownTask} : Node closes the broadcast local node logoff task</li>
 * </ul>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DiscoveryMemberLookup extends AbstractMemberLookup {

	NAsyncHttpClient asyncHttpClient = HttpClientManager
			.newAsyncHttpClient(ServerMemberManager.class.getCanonicalName());

	Set<Task> tasks = new HashSet<>();

	/**
	 * Seed node preservation
	 */
	Set<Member> seedMembers = new HashSet<>();

	@Override
	public void init(ServerMemberManager memberManager) throws NacosException {
		super.init(memberManager);
		try {
			List<String> members = ApplicationUtils.readClusterConf();
			MemberUtils.readServerConf(members, memberManager);
		}
		catch (FileNotFoundException e) {
			String clusters = ApplicationUtils.getMemberList();
			if (StringUtils.isNotBlank(clusters)) {
				String[] details = clusters.split(",");
				List<String> members = new ArrayList<>();
				for (String item : details) {
					members.add(item.trim());
				}
				MemberUtils.readServerConf(members, memberManager);
			}
		}
		catch (Throwable ex) {
			throw new NacosException(NacosException.SERVER_ERROR, ex);
		}

		if (memberManager.getServerList().isEmpty()) {
			throw new NacosException(NacosException.SERVER_ERROR,
					"Failed to initialize the member node, is empty");
		}

		seedMembers = Collections
				.unmodifiableSet(new HashSet<>(memberManager.getServerList().values()));
	}

	@Override
	public void run() {
		// Whether to enable the node self-discovery function that comes with nacos
		// The reason why instance properties are not used here is so that
		// the hot update mechanism can be implemented later
		MemberListSyncTask syncTask = new MemberListSyncTask();
		MemberDeadBroadcastTask broadcastTask = new MemberDeadBroadcastTask();

		GlobalExecutor.schedulePingJob(syncTask, 5_000L);
		GlobalExecutor.scheduleBroadCastJob(broadcastTask, 10_000L);

		tasks.add(syncTask);
		tasks.add(broadcastTask);
	}

	@Override
	public void destroy() {
		for (Task task : tasks) {
			task.shutdown();
		}
		GlobalExecutor.runWithoutThread(new MemberDeadBroadcastTask());
	}

	// Synchronize cluster member list information to a node

	class MemberListSyncTask extends Task {

		private final GenericType<RestResult<String>> reference = new GenericType<RestResult<String>>() {
		};

		private final GenericType<Collection<String>> memberReference = new GenericType<Collection<String>>() {
		};

		@Override
		public void executeBody() {
			TimerContext.start("MemberPingTask");
			try {
				final Member self = memberManager.getSelf();
				// self node information is not ready
				if (!self.check()) {
					return;
				}

				Collection<Member> members = MemberUtils.kRandom(memberManager, member -> {
					// local node or node check failed will not perform task processing
					if (memberManager.isSelf(member) || !member.check()) {
						return false;
					}
					NodeState state = member.getState();
					return !(state == NodeState.DOWN || state == NodeState.SUSPICIOUS);
				});

				for (Member member : members) {
					// If the cluster self-discovery is turned on, the information is synchronized with the node
					String url = "http://" + member.getAddress() + memberManager
							.getContextPath() + Commons.NACOS_CORE_CONTEXT
							+ "/cluster/simple/nodes";

					if (shutdown) {
						return;
					}

					asyncHttpClient.post(url, Header.EMPTY, Query.EMPTY, self,
							reference.getType(), new Callback<String>() {
								@Override
								public void onReceive(RestResult<String> result) {
									if (result.ok()) {
										Loggers.CLUSTER
												.debug("success ping to node : {}, result : {}",
														member, result);

										final String data = result.getData();
										if (StringUtils.isNotBlank(data)) {
											discovery(data);
										}
										MemberUtils.onSuccess(member, memberManager);
									}
									else {
										Loggers.CLUSTER
												.warn("An exception occurred while reporting their "
																+ "information to the node : {}, error : {}",
														member.getAddress(),
														result.getMessage());
										MemberUtils.onFail(member, memberManager);
									}
								}

								@Override
								public void onError(Throwable e) {
									Loggers.CLUSTER
											.error("An exception occurred while reporting their "
															+ "information to the node : {}, error : {}",
													member.getAddress(), e);
									MemberUtils.onFail(member, memberManager);
								}
							});
				}
			}
			catch (Exception e) {
				Loggers.CLUSTER.error("node state report task has error : {}",
						ExceptionUtil.getAllExceptionMsg(e));
			}
			finally {
				TimerContext.end(Loggers.CLUSTER);
			}
		}

		@Override
		protected void after() {
			GlobalExecutor.schedulePingJob(this, 5_000L);
		}

		private void discovery(String result) {
			try {
				Collection<String> members = ResponseHandler
						.convert(result, memberReference.getType());
				MemberUtils
						.readServerConf(Objects.requireNonNull(members), memberManager);
			}
			catch (Exception e) {
				Loggers.CLUSTER.error("The cluster self-detects a problem");
			}
		}

	}

	class MemberDeadBroadcastTask extends Task {

		private final GenericType<RestResult<String>> reference = new GenericType<RestResult<String>>() {
		};

		@Override
		protected void executeBody() {
			Collection<Member> members = memberManager.allMembers();
			Collection<Member> waitRemove = new ArrayList<>();
			members.forEach(member -> {
				if (NodeState.DOWN.equals(member.getState()) && !seedMembers
						.contains(member)) {
					waitRemove.add(member);
				}
			});

			List<Member> waitBroadcast = MemberUtils.kRandom(memberManager,
					member -> !NodeState.DOWN.equals(member.getState()));

			for (Member member : waitBroadcast) {
				final String url = HttpUtils.buildUrl(false, member.getAddress(),
						memberManager.getContextPath(), Commons.NACOS_CORE_CONTEXT,
						"/cluster/server/leave");
				if (shutdown) {
					return;
				}

				asyncHttpClient.post(url, Header.EMPTY, Query.EMPTY, waitRemove,
						reference.getType(), new Callback<String>() {
							@Override
							public void onReceive(RestResult<String> result) {
								if (result.ok()) {
									Loggers.CLUSTER
											.debug("The node : [{}] success to process the request",
													member);
									MemberUtils.onSuccess(member, memberManager);
								}
								else {
									Loggers.CLUSTER
											.warn("The node : [{}] failed to process the request, response is : {}",
													member, result);
									MemberUtils.onFail(member, memberManager);
								}
							}

							@Override
							public void onError(Throwable throwable) {
								Loggers.CLUSTER
										.error("Failed to communicate with the node : {}",
												member);
								MemberUtils.onFail(member, memberManager);
							}
						});
			}
		}

		@Override
		protected void after() {
			GlobalExecutor.scheduleBroadCastJob(this, 5_000L);
		}
	}

	class MemberShutdownTask implements Runnable {

		private NSyncHttpClient httpClient = HttpClientManager
				.newSyncHttpClient(MemberShutdownTask.class.getCanonicalName());

		private final GenericType<RestResult<String>> typeReference = new GenericType<RestResult<String>>() {
		};

		@Override
		public void run() {
			try {
				Collection<Member> body = new ArrayList<>(
						Collections.singletonList(memberManager.getSelf()));
				Loggers.CLUSTER.info("Start broadcasting this node logout");
				memberManager.allMembers().forEach(member -> {
					// don't have to broadcast yourself
					if (memberManager.isSelf(member)) {
						return;
					}
					final String url =
							"http://" + member.getAddress() + memberManager.getContextPath()
									+ Commons.NACOS_CORE_CONTEXT + "/cluster/server/leave";
					try {
						RestResult<String> result = httpClient
								.post(url, Header.EMPTY, Query.EMPTY, body,
										typeReference.getType());
						Loggers.CLUSTER
								.info("{} the response of the target node to this logout operation : {}",
										member, result);
					}
					catch (Throwable e) {
						Loggers.CLUSTER.error("shutdown execute has error : {}", e);
					}
				});
			}
			finally {
				try {
					httpClient.close();
				}
				catch (Exception e) {
					Loggers.CLUSTER.error("error : {}", e);
				}
			}
		}

	}
}
