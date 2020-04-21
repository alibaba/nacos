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
import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.cluster.lookup.MemberLookup;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;

/**
 * Cluster node management in Nacos
 *
 * {@link ServerMemberManager#init()} Cluster node manager initialization
 * {@link ServerMemberManager#shutdown()} The cluster node manager is down
 * {@link ServerMemberManager#getSelf()} Gets local node information
 * {@link ServerMemberManager#getServerList()} Gets the cluster node dictionary
 * {@link ServerMemberManager#getMemberAddressInfos()} Gets the address information of the healthy member node
 * {@link ServerMemberManager#getPort()} Gets the port number of this node
 * {@link ServerMemberManager#allMembers()} Gets a list of member information objects
 * {@link ServerMemberManager#allMembersWithoutSelf()} Gets a list of cluster member nodes with the exception of this node
 * {@link ServerMemberManager#hasMember(String)} Is there a node
 * {@link ServerMemberManager#memberJoin(Collection)} Node join, can automatically trigger
 * {@link ServerMemberManager#memberLeave(Collection)} When the node leaves, only the interface call can be manually triggered
 * {@link ServerMemberManager#update(Member)} Update the target node information
 * {@link ServerMemberManager#indexOf(String)} The index location of a node
 * {@link ServerMemberManager#isFirstIp()} Is it the first node
 * {@link ServerMemberManager#isUnHealth(String)} Whether the target node is healthy
 * {@link ServerMemberManager#initLookup()} Initializes the addressing mode
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component(value = "serverMemberManager")
@SuppressWarnings("all")
public class ServerMemberManager
		implements ApplicationListener<WebServerInitializedEvent> {

	private final ServletContext servletContext;
	private final NAsyncHttpClient asyncHttpClient = HttpClientManager
			.newAsyncHttpClient(ServerMemberManager.class.getCanonicalName());
	private Map<String, Member> serverList = new ConcurrentSkipListMap<>();
	private volatile boolean isInIpList = true;
	@Value("${server.port:8848}")
	private int port;
	private Member self;
	private String localAddress;
	private boolean isHealthCheck = true;
	private MemberLookup lookup;
	private final MemberInfoReportTask syncTask = new MemberInfoReportTask();

	// here is always the node information of the "UP" state
	private Set<String> memberAddressInfos = new ConcurrentHashSet<>();

	public ServerMemberManager(ServletContext servletContext) {
		this.servletContext = servletContext;
		MemberUtils.setManager(this);
	}

	@PostConstruct
	public void init() throws NacosException {
		Loggers.CORE.info("Nacos-related cluster resource initialization");
		this.port = ApplicationUtils.getProperty("server.port", Integer.class, 8848);
		this.localAddress = InetUtils.getSelfIp() + ":" + port;
		this.isHealthCheck = Boolean
				.parseBoolean(ApplicationUtils.getProperty("isHealthCheck", "true"));

		// register NodeChangeEvent publisher to NotifyManager
		registerClusterEvent();

		// Initializes the lookup mode
		initLookup();

		if (serverList.isEmpty()) {
			throw new NacosException(NacosException.SERVER_ERROR,
					"Failed to initialize the list of member nodes");
		}

		getSelf().setState(NodeState.STARTING);
		Loggers.CORE.info("The cluster resource is initialized");
	}

	private void initLookup() throws NacosException {
		this.lookup = LookupFactory.createLookUp();
		this.lookup.addObserver(new Observer() {
			@Override
			public void update(Observable o, Object arg) {
				Collection<Member> tmp = lookup.getMembers();
				memberJoin(tmp);
			}
		});
		this.lookup.start();
	}

	private void registerClusterEvent() {
		// Register node change events
		NotifyCenter.registerToPublisher(MemberChangeEvent::new, MemberChangeEvent.class,
				ApplicationUtils.getProperty("nacos.member-change-event.queue.size",
						Integer.class, 128));
		// The address information of this node needs to be dynamically modified
		// when registering the IP change of this node
		NotifyCenter.registerSubscribe(new Subscribe<InetUtils.IPChangeEvent>() {
			@Override
			public void onEvent(InetUtils.IPChangeEvent event) {
				String oldAddress = event.getOldIp() + ":" + port;
				String newAddress = event.getNewIp() + ":" + port;
				localAddress = newAddress;
				Member member = serverList.get(oldAddress);
				if (Objects.nonNull(member)) {
					member.setIp(event.getNewIp());
					serverList.remove(oldAddress);
					serverList.put(newAddress, member);
				}
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return InetUtils.IPChangeEvent.class;
			}
		});
	}

	public void update(Member newMember) {
		Loggers.CLUSTER.debug("Node information update : {}", newMember);

		String address = newMember.getAddress();

		if (!serverList.containsKey(address)) {
			memberJoin(new ArrayList<>(Arrays.asList(newMember)));
		}

		serverList.computeIfPresent(address, new BiFunction<String, Member, Member>() {
			@Override
			public Member apply(String s, Member member) {
				if (!NodeState.UP.equals(newMember.getState())) {
					memberAddressInfos.remove(newMember.getAddress());
				}
				MemberUtils.copy(newMember, member);
				return member;
			}
		});
	}

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

	public Member getSelf() {
		if (Objects.isNull(self)) {
			self = serverList.get(localAddress);
		}
		return self;
	}

	public Collection<Member> allMembers() {
		// We need to do a copy to avoid affecting the real data
		return new ArrayList<>(serverList.values());
	}

	public List<Member> allMembersWithoutSelf() {
		List<Member> members = new ArrayList<>(serverList.values());
		members.remove(getSelf());
		return members;
	}

	public void memberJoin(Collection<Member> members) {
		for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {
			final Member newMember = iterator.next();
			final String address = newMember.getAddress();

			// If the current node is already in the node list, remove it;
			// if all the node lists to be added are removed, there is no node list change event
			if (serverList.containsKey(address)) {
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

		Collection<Member> memberCollection = allMembers();

		// Persist the current cluster node information to cluster.conf
		MemberUtils.syncToFile(memberCollection);
		Loggers.CLUSTER.warn("have new node join : {}", members);
		NotifyCenter.publishEvent(
				MemberChangeEvent.builder().changeNodes(members).allNodes(memberCollection)
						.build());
	}

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

		Collection<Member> memberCollection = allMembers();

		// Persist the current cluster node information to cluster.conf
		MemberUtils.syncToFile(memberCollection);
		Loggers.CLUSTER.warn("have node leave : {}", members);
		NotifyCenter.publishEvent(
				MemberChangeEvent.builder().changeNodes(members).allNodes(memberCollection)
						.build());
	}

	public boolean isFirstIp() {
		return 1 == indexOf(this.localAddress);
	}

	public boolean isUnHealth(String address) {
		Member member = serverList.get(address);
		if (member == null) {
			return false;
		}
		return !NodeState.UP.equals(member.getState());
	}

	public boolean isSelf(Member member) {
		return Objects.equals(member.getAddress(), this.localAddress);
	}

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		getSelf().setState(NodeState.UP);
		// For containers that have started, stop all messages from being published late
		NotifyCenter.stopDeferPublish();
		GlobalExecutor.scheduleByCommon(this.syncTask, 5_000L);
		ApplicationUtils.setPort(event.getWebServer().getPort());
		ApplicationUtils.setLocalAddress(this.localAddress);
		Loggers.CLUSTER.info("This node is ready to provide external services");
	}

	@PreDestroy
	public void shutdown() throws NacosException {
		getSelf().setState(NodeState.DOWN);
		syncTask.shutdown();
		LookupFactory.destroy();
	}

	@VisibleForTesting
	public void updateMember(Member member) {
		serverList.put(member.getAddress(), member);
	}

	public Set<String> getMemberAddressInfos() {
		return memberAddressInfos;
	}

	@VisibleForTesting
	public void setMemberAddressInfos(Set<String> memberAddressInfos) {
		this.memberAddressInfos = memberAddressInfos;
	}

	public Map<String, Member> getServerList() {
		return Collections.unmodifiableMap(serverList);
	}

	public boolean isInIpList() {
		return isInIpList;
	}

	public boolean isHealthCheck() {
		return isHealthCheck;
	}

	public int getPort() {
		return port;
	}

	// Synchronize the metadata information of a node

	class MemberInfoReportTask extends Task {

		private final GenericType<RestResult<Member>> reference = new GenericType<RestResult<Member>>() {
		};

		private int cursor = 0;

		@Override
		protected void executeBody() {
			List<Member> members = ServerMemberManager.this.allMembersWithoutSelf();
			this.cursor = (this.cursor + 1) % members.size();
			Member target = members.get(cursor);

			Loggers.CLUSTER.debug("report the metadata to the node : {}", target);

			final String url = HttpUtils.buildUrl(false, target.getAddress(),
					ApplicationUtils.getContextPath(), Commons.NACOS_CORE_CONTEXT,
					"/cluster/report");

			asyncHttpClient
					.post(url, Header.EMPTY, Query.EMPTY, getSelf(), reference.getType(),
							new Callback<Member>() {
								@Override
								public void onReceive(RestResult<Member> result) {
									if (result.ok()) {
										MemberUtils.onSuccess(
												MemberUtils.singleParse(target.getAddress()));
									}
									else {
										Loggers.CLUSTER
												.warn("failed to pull new info from target node : {}, result : {}",
														target, result);
										MemberUtils.onFail(MemberUtils
												.singleParse(target.getAddress()));
									}
								}

								@Override
								public void onError(Throwable throwable) {
									Loggers.CLUSTER
											.error("failed to pull new info from target node : {}, error : {}",
													target, throwable);
									MemberUtils.onFail(MemberUtils
											.singleParse(target.getAddress()));
								}
							});
		}

		@Override
		protected void after() {
			GlobalExecutor.scheduleByCommon(this, 2_000L);
		}
	}

}
