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
import com.alibaba.nacos.common.http.NAsyncHttpClient;
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
import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *     │                   │                                                     │
 *     │                   │                                                     │
 *     │                   ▼                                                     │
 *     │  ┌────────────────────────────────┐                                     │
 *     │  │     read init members from     │                                     │
 *     │  │        cluster.conf or         │                                     │
 *     │  └────────────────────────────────┘                                     │
 *     │                   │                                                     │
 *     │                   │                                                     │
 *     │                   │                                                     │                                      ┌────────────────────────────────────┐
 *     │                   ▼                                                     │                                      │                                    │
 *     │        ┌─────────────────────┐              ┌─────────────────────────┐ │                                      │              Member B              │
 *     │        │  init gossip task   │─────────────▶│ MemberListSyncTask      │─┼──────[ip1:port,ip2:port,ip3:port]────│    [ip1:port,ip2.port,ip3.port]    │
 *     │        └─────────────────────┘              └─────────────────────────┘ │                                      │   {adweight:"",site:"",state:""}   │
 *     │                                                                         │                                      │                                    │
 *     │                                                                         │                                      └────────────────────────────────────┘
 *     └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <ul>
 *     <li>{@link MemberListSyncTask} : Cluster node list synchronization tasks</li>
 * </ul>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DiscoveryMemberLookup extends AbstractMemberLookup {

	NAsyncHttpClient asyncHttpClient = HttpClientManager
			.newAsyncHttpClient(ServerMemberManager.class.getCanonicalName());

	MemberListSyncTask syncTask;

	@Override
	public void start() throws NacosException {
		if (start.compareAndSet(false, true)) {
			Collection<Member> tmpMembers = new ArrayList<>();

			try {
				List<String> tmp = ApplicationUtils.readClusterConf();
				tmpMembers.addAll(MemberUtils.readServerConf(tmp));
			}
			catch (Throwable ex) {
				throw new NacosException(NacosException.SERVER_ERROR, ex);
			}

			afterLookup(tmpMembers);

			// Whether to enable the node self-discovery function that comes with nacos
			// The reason why instance properties are not used here is so that
			// the hot update mechanism can be implemented later
			syncTask = new MemberListSyncTask();

			GlobalExecutor.scheduleByCommon(syncTask, 5_000L);
		}
	}

	@Override
	public void destroy() {
		syncTask.shutdown();
	}

	// Synchronize cluster member list information to a node

	class MemberListSyncTask extends Task {

		private final GenericType<RestResult<Collection<String>>> reference = new GenericType<RestResult<Collection<String>>>() {
		};

		@Override
		public void executeBody() {
			TimerContext.start("MemberListSyncTask");
			try {
				Collection<Member> kMembers = MemberUtils.kRandom(memberManager.allMembers(), member -> {
					// local node or node check failed will not perform task processing
					if (!member.check()) {
						return false;
					}
					NodeState state = member.getState();
					return !(state == NodeState.DOWN || state == NodeState.SUSPICIOUS);
				});

				for (Member member : kMembers) {
					// If the cluster self-discovery is turned on, the information is synchronized with the node
					String url = "http://" + member.getAddress() + ApplicationUtils
							.getContextPath() + Commons.NACOS_CORE_CONTEXT
							+ "/cluster/simple/nodes";

					if (shutdown) {
						return;
					}

					asyncHttpClient.get(url, Header.EMPTY, Query.EMPTY, reference.getType(), new Callback<Collection<String>>() {
								@Override
								public void onReceive(RestResult<Collection<String>> result) {
									if (result.ok()) {
										Loggers.CLUSTER
												.debug("success ping to node : {}, result : {}",
														member, result);

										final Collection<String> data = result.getData();
										if (CollectionUtils.isNotEmpty(data)) {
											discovery(data);
										}
										MemberUtils.onSuccess(member);
									}
									else {
										Loggers.CLUSTER
												.warn("An exception occurred while reporting their "
																+ "information to the node : {}, error : {}",
														member.getAddress(),
														result.getMessage());
										MemberUtils.onFail(member);
									}
								}

								@Override
								public void onError(Throwable e) {
									Loggers.CLUSTER
											.error("An exception occurred while reporting their "
															+ "information to the node : {}, error : {}",
													member.getAddress(), e);
									MemberUtils.onFail(member);
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
			GlobalExecutor.scheduleByCommon(this, 5_000L);
		}

		private void discovery(Collection<String> result) {
			try {
				afterLookup(MemberUtils.readServerConf(Objects.requireNonNull(result)));
			}
			catch (Exception e) {
				Loggers.CLUSTER.error("The cluster self-detects a problem");
			}
		}

	}

}
