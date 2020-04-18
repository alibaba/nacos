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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Constants;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.PropertyUtil;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;

/**
 * Cluster node management in Nacos
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component(value = "serverMemberManager")
@SuppressWarnings("all")
public class ServerMemberManager
		implements ApplicationListener<WebServerInitializedEvent>, MemberManager {

	private final ServletContext servletContext;
	private final NAsyncHttpClient asyncHttpClient = HttpClientManager
			.newAsyncHttpClient(ServerMemberManager.class.getCanonicalName());
	private Map<String, Member> serverList = new ConcurrentSkipListMap<>();
	private volatile boolean isInIpList = true;
	private String contextPath = "";
	@Value("${server.port:8848}")
	private int port;
	@Value("${useAddressServer:false}")
	private boolean isUseAddressServer;
	private Member self;
	private String localAddress;
	private boolean isHealthCheck = true;
	private final int memberChangeEventMaxQueueSize = Integer
			.getInteger("nacos.member-change-event.queue.size", 128);
	private final MemberInfoSyncTask syncTask = new MemberInfoSyncTask();

	// here is always the node information of the "UP" state
	private Set<String> memberAddressInfos = new ConcurrentHashSet<>();

	public ServerMemberManager(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	@PostConstruct
	@Override
	public void init() throws NacosException {
		Loggers.CORE.info("Nacos-related cluster resource initialization");
		this.port = ApplicationUtils.getProperty("server.port", Integer.class, 8848);
		this.localAddress = InetUtils.getSelfIp() + ":" + port;

		isHealthCheck = Boolean
				.parseBoolean(ApplicationUtils.getProperty("isHealthCheck", "true"));

		// register NodeChangeEvent publisher to NotifyManager
		registerClusterEvent();

		// Initializes the addressing mode
		LookupFactory.initLookUp(this);

		Loggers.CORE.info("The cluster resource is initialized");
	}

	private void registerClusterEvent() {
		// Register node change events
		NotifyCenter.registerToPublisher(MemberChangeEvent::new, MemberChangeEvent.class,
				memberChangeEventMaxQueueSize);
		// Register the web container initialization completion event
		NotifyCenter.registerToSharePublisher(ServerInitializedEvent::new,
				ServerInitializedEvent.class);
		// Register node isolation events
		NotifyCenter.registerToSharePublisher(IsolationEvent::new, IsolationEvent.class);
		// Register node recover events
		NotifyCenter.registerToSharePublisher(RecoverEvent::new, RecoverEvent.class);

		// Handles events related to node isolation and recovery
		NotifyCenter.registerSubscribe(new SmartSubscribe() {

			@Override
			public void onEvent(Event event) {
				if (event instanceof IsolationEvent) {
					self.setState(NodeState.ISOLATION);
					return;
				}
				if (event instanceof RecoverEvent) {
					self.setState(NodeState.UP);
					return;
				}
			}

			@Override
			public boolean canNotify(Event event) {
				boolean i = event instanceof IsolationEvent;
				boolean r = event instanceof RecoverEvent;
				return i || r;
			}
		});
	}

	@Override
	public void update(Member newMember) {
		String address = newMember.getAddress();

		if (!serverList.containsKey(address)) {
			memberJoin(new ArrayList<>(Arrays.asList(newMember)));
		}

		serverList.computeIfPresent(address, new BiFunction<String, Member, Member>() {
			@Override
			public Member apply(String s, Member member) {
				member.setFailAccessCnt(0);
				member.setState(NodeState.UP);
				MemberUtils.copy(newMember, member);
				return member;
			}
		});
	}

	@Override
	public int indexOf(String address) {
		int index = 1;
		for (Map.Entry<String, Member> entry : serverList.entrySet()) {
			if (Objects.equals(entry.getKey(), address)) {
				return index;
			}
			index++;
		}
		return index;
	}

	@Override
	public boolean hasMember(String address) {
		boolean result = serverList.containsKey(address);
		if (!result) {
			for (Map.Entry<String, Member> entry : serverList.entrySet()) {
				if (StringUtils.contains(entry.getKey(), address)) {
					result = true;
					break;
				}
			}
		}
		return result;
	}

	@Override
	public Member getSelf() {
		if (Objects.isNull(self)) {
			self = serverList.get(localAddress);
		}
		return self;
	}

	@Override
	public Collection<Member> allMembers() {
		// We need to do a copy to avoid affecting the real data
		return new ArrayList<>(serverList.values());
	}

	@Override
	public void memberJoin(Collection<Member> members) {
		for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {
			final Member newMember = iterator.next();
			final String address = newMember.getAddress();
			if (serverList.containsKey(address)) {
				iterator.remove();
				continue;
			}
			NodeState state = newMember.getState();
			if (state == NodeState.DOWN || state == NodeState.SUSPICIOUS) {
				iterator.remove();
				continue;
			}

			// Ensure that the node is created only once
			serverList.computeIfAbsent(address, s -> {
				memberAddressInfos.add(address);
				return newMember;
			});
			serverList
					.computeIfPresent(address, new BiFunction<String, Member, Member>() {
						@Override
						public Member apply(String s, Member member) {
							MemberUtils.copy(newMember, member);
							return member;
						}
					});
		}

		if (members.isEmpty()) {
			return;
		}

		// Persist the current cluster node information to cluster.conf
		MemberUtils.syncToFile(allMembers());
		Loggers.CLUSTER.warn("have new node join : {}", members);
		NotifyCenter.publishEvent(
				MemberChangeEvent.builder().changeNodes(members).allNodes(allMembers())
						.build());
	}

	@Override
	public void memberLeave(Collection<Member> members) {
		for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {
			Member member = iterator.next();
			final String address = member.getAddress();
			if (StringUtils.equals(address, localAddress)) {
				iterator.remove();
				continue;
			}
			serverList
					.computeIfPresent(address, new BiFunction<String, Member, Member>() {
						@Override
						public Member apply(String s, Member member) {
							memberAddressInfos.remove(address);
							return null;
						}
					});
		}

		if (members.isEmpty()) {
			return;
		}

		// Persist the current cluster node information to cluster.conf
		MemberUtils.syncToFile(allMembers());
		Loggers.CLUSTER.warn("have node leave : {}", members);
		NotifyCenter.publishEvent(
				MemberChangeEvent.builder().changeNodes(members).allNodes(allMembers())
						.build());
	}

	@Override
	public void subscribe(MemberChangeListener listener) {
		NotifyCenter.registerSubscribe(listener);
	}

	@Override
	public void unSubscribe(MemberChangeListener listener) {
		NotifyCenter.deregisterSubscribe(listener);
	}

	public String getContextPath() {
		if (StringUtils.isBlank(contextPath)) {
			String contextPath = PropertyUtil.getProperty(Constants.WEB_CONTEXT_PATH);
			// If you can't find it, check it from Sping Environment
			if (StringUtils.isBlank(contextPath)) {
				contextPath = ApplicationUtils.getProperty(Constants.WEB_CONTEXT_PATH);
			}
			if (Constants.ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
				return StringUtils.EMPTY;
			}
			else {
				return contextPath;
			}
		}
		return contextPath;
	}

	@Override
	public boolean isFirstIp() {
		return 1 == indexOf(localAddress);
	}

	@Override
	public boolean isUnHealth(String address) {
		Member member = serverList.get(address);
		if (member == null) {
			return false;
		}
		return !NodeState.UP.equals(member.getState());
	}

	public boolean isSelf(Member member) {
		return Objects.equals(member.getAddress(), localAddress);
	}

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
        // For containers that have started, stop all messages from being published late
	    NotifyCenter.stopDeferPublish();

		GlobalExecutor.schedulePullJob(syncTask, 5_000L);
		NotifyCenter.publishEvent(
				new ServerInitializedEvent((WebServerInitializedEvent) event,
						servletContext));
	}

	@PreDestroy
	@Override
	public void shutdown() throws NacosException {
	    syncTask.shutdown();
		LookupFactory.destroy();
		getSelf().setState(NodeState.DOWN);
	}

	@VisibleForTesting
	public void updateMember(Member member) {
		serverList.put(member.getAddress(), member);
	}

	public Set<String> getMemberAddressInfos() {
		return memberAddressInfos;
	}

	public void setMemberAddressInfos(Set<String> memberAddressInfos) {
		this.memberAddressInfos = memberAddressInfos;
	}

	public Map<String, Member> getServerList() {
		return Collections.unmodifiableMap(serverList);
	}

	public boolean isUseAddressServer() {
		return isUseAddressServer;
	}

	public boolean isInIpList() {
		return isInIpList;
	}

	public boolean isHealthCheck() {
		return isHealthCheck;
	}

	public ServletContext getServletContext() {
		return servletContext;
	}

	public boolean getUseAddressServer() {
		return isUseAddressServer;
	}

	public void setUseAddressServer(boolean useAddressServer) {
		isUseAddressServer = useAddressServer;
	}

	public int getPort() {
		return port;
	}

	// Synchronize the metadata information of a node

	class MemberInfoSyncTask extends Task {

		private final GenericType<RestResult<Member>> reference = new GenericType<RestResult<Member>>() {
		};

		private int cursor = 0;

		@Override
		protected void executeBody() {
			Set<String> members = ServerMemberManager.this.getMemberAddressInfos();
			this.cursor = (this.cursor + 1) % members.size();
			String[] ss = members.toArray(new String[0]);
			String target = ss[cursor];

			final String url = HttpUtils
					.buildUrl(false, target, ServerMemberManager.this.getContextPath(),
							Commons.NACOS_CORE_CONTEXT, "/cluster/self");

			if (shutdown) {
				return;
			}

			asyncHttpClient.get(url, Header.EMPTY, Query.EMPTY, reference.getType(),
					new Callback<Member>() {
						@Override
						public void onReceive(RestResult<Member> result) {
							if (result.ok()) {
								Loggers.CLUSTER
										.debug("success pull from node : {}, result : {}",
												target, result);
								ServerMemberManager.this.update(result.getData());
								MemberUtils.onSuccess(MemberUtils.parse(target),
										ServerMemberManager.this);
							}
							else {
								Loggers.CLUSTER
										.warn("failed to pull new info from target node : {}, result : {}",
												target, result);
								MemberUtils.onFail(MemberUtils.parse(target),
										ServerMemberManager.this);
							}
						}

						@Override
						public void onError(Throwable throwable) {
							Loggers.CLUSTER
									.error("failed to pull new info from target node : {}, error : {}",
											target, throwable);
							MemberUtils.onFail(MemberUtils.parse(target),
									ServerMemberManager.this);
						}
					});
		}

		@Override
		protected void after() {
			GlobalExecutor.schedulePullJob(this, 2_000L);
		}
	}

}
