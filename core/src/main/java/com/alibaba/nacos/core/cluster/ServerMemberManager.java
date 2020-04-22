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
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

/**
 * Cluster node management in Nacos
 * <p>
 * {@link ServerMemberManager#init()} Cluster node manager initialization
 * {@link ServerMemberManager#shutdown()} The cluster node manager is down
 * {@link ServerMemberManager#getSelf()} Gets local node information
 * {@link ServerMemberManager#getServerList()} Gets the cluster node dictionary
 * {@link ServerMemberManager#getMemberAddressInfos()} Gets the address information of the healthy member node
 * {@link ServerMemberManager#allMembers()} Gets a list of member information objects
 * {@link ServerMemberManager#allMembersWithoutSelf()} Gets a list of cluster member nodes with the exception of this node
 * {@link ServerMemberManager#hasMember(String)} Is there a node
 * {@link ServerMemberManager#memberChange(Collection)} The final node list changes the method, making the full size more
 * {@link ServerMemberManager#memberJoin(Collection)} Node join, can automatically trigger
 * {@link ServerMemberManager#memberLeave(Collection)} When the node leaves, only the interface call can be manually triggered
 * {@link ServerMemberManager#update(Member)} Update the target node information
 * {@link ServerMemberManager#isUnHealth(String)} Whether the target node is healthy
 * {@link ServerMemberManager#initAndStartLookup()} Initializes the addressing mode
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

	/**
	 * Cluster node list
	 */
	private TreeMap<String, Member> serverList = new TreeMap<>();

	/**
	 * Is this node in the cluster list
	 */
	private volatile boolean isInIpList = true;

	/**
	 * port
	 */
	@Value("${server.port:8848}")
	private int port;

	/**
	 * This node information object
	 */
	private Member self;
	private String localAddress;

	/**
	 * Addressing pattern instances
	 */
	private MemberLookup lookup;

	/**
	 * here is always the node information of the "UP" state
	 */
	private Set<String> memberAddressInfos = new HashSet<>();

	/**
	 * Broadcast this node element information task
	 */
	private final MemberInfoReportTask infoReportTask = new MemberInfoReportTask();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
	private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();

	public ServerMemberManager(ServletContext servletContext) {
		this.servletContext = servletContext;
		ApplicationUtils.setContextPath(servletContext.getContextPath());
		MemberUtils.setManager(this);
	}

	@PostConstruct
	public void init() throws NacosException {
		Loggers.CORE.info("Nacos-related cluster resource initialization");
		this.port = ApplicationUtils.getProperty("server.port", Integer.class, 8848);
		this.localAddress = InetUtils.getSelfIp() + ":" + port;

		// register NodeChangeEvent publisher to NotifyManager
		registerClusterEvent();

		// Initializes the lookup mode
		initAndStartLookup();

		if (serverList.isEmpty()) {
			throw new NacosException(NacosException.SERVER_ERROR,
					"cannot get serverlist, so exit.");
		}

		getSelf().setState(NodeState.STARTING);
		Loggers.CORE.info("The cluster resource is initialized");
	}

	private void initAndStartLookup() throws NacosException {
		this.lookup = LookupFactory.createLookUp(this);
		this.lookup.start();
	}

	public void swithLookup(String name) throws NacosException {
		this.lookup = LookupFactory.switchLookup(name, this);
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
				writeLock.lock();
				try {
					Member member = serverList.get(oldAddress);
					if (Objects.nonNull(member)) {
						member.setIp(event.getNewIp());
						serverList.remove(oldAddress);
						serverList.put(newAddress, member);

						memberAddressInfos.remove(oldAddress);
						memberAddressInfos.add(newAddress);

					}
					self = null;
					localAddress = newAddress;
				}
				finally {
					writeLock.unlock();
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

		writeLock.lock();
		try {
			if (!serverList.containsKey(address)) {
				memberJoin(Collections.singletonList(newMember));
			}
			serverList
					.computeIfPresent(address, new BiFunction<String, Member, Member>() {
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
		finally {
			writeLock.unlock();
		}
	}

	public boolean hasMember(String address) {
		readLock.lock();
		try {
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
		finally {
			readLock.unlock();
		}
	}

	public Member getSelf() {
		if (Objects.isNull(self)) {
			readLock.lock();
			try {
				self = serverList.get(localAddress);
			}
			finally {
				readLock.unlock();
			}
		}
		return self;
	}

	public Collection<Member> allMembers() {
		readLock.lock();
		try {
			// We need to do a copy to avoid affecting the real data
			return new ArrayList<>(serverList.values());
		}
		finally {
			readLock.unlock();
		}
	}

	public List<Member> allMembersWithoutSelf() {
		readLock.lock();
		try {
			List<Member> members = new ArrayList<>(serverList.values());
			members.remove(getSelf());
			return members;
		}
		finally {
			readLock.unlock();
		}
	}

	public void memberChange(Collection<Member> members) {

		if (members == null || members.isEmpty()) {
			return;
		}

		boolean isContainSelfIp = members.stream().anyMatch(ipPortTmp -> Objects.equals(localAddress, ipPortTmp.getAddress()));

		if (isContainSelfIp) {
			isInIpList = true;
		} else {
			isInIpList = false;
			members.add(getSelf());
			Loggers.CLUSTER.error("[serverlist] self ip {} not in serverlist {}", getSelf(), members);
		}

		writeLock.lock();
		boolean hasChange = false;
		try {
			Map<String, Member> tmpMap = new HashMap<>();
			Set<String> tmpAddressInfo = new HashSet<>();
			for (Member member : members) {
				final String address = member.getAddress();

				if (!serverList.containsKey(address)) {
					hasChange = true;
				}

				// Ensure that the node is created only once
				tmpMap.computeIfAbsent(address, s -> {
					tmpAddressInfo.add(address);
					return member;
				});
			}

			serverList.clear();
			serverList.putAll(tmpMap);

			memberAddressInfos.clear();
			memberAddressInfos.addAll(tmpAddressInfo);
		}
		finally {
			writeLock.unlock();
		}

		// Persist the current cluster node information to cluster.conf
		if (hasChange) {
			MemberUtils.syncToFile(members);
			Loggers.CLUSTER.warn("member has changed : {}", members);
			NotifyCenter.publishEvent(MemberChangeEvent.builder().allNodes(members).build());
		}
	}

	public void memberJoin(Collection<Member> members) {
		writeLock.lock();
		try {
			Set<Member> set = new HashSet<>();
			set.addAll(members);
			set.addAll(allMembers());
			memberChange(set);
		}
		finally {
			writeLock.unlock();
		}
	}

	public void memberLeave(Collection<Member> members) {
		writeLock.lock();
		try {
			Set<Member> set = new HashSet<>();
			set.addAll(allMembers());
			set.removeAll(members);
			memberChange(set);
		}
		finally {
			writeLock.unlock();
		}
	}

	public boolean isUnHealth(String address) {
		readLock.lock();
		try {
			Member member = serverList.get(address);
			if (member == null) {
				return false;
			}
			return !NodeState.UP.equals(member.getState());
		}
		finally {
			readLock.unlock();
		}
	}

	public boolean isFirstIp() {
		readLock.lock();
		try {
			return Objects.equals(serverList.firstKey(), this.localAddress);
		} finally {
			readLock.unlock();
		}
	}

	public boolean isSelf(Member member) {
		return Objects.equals(member.getAddress(), this.localAddress);
	}

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		getSelf().setState(NodeState.UP);
		// For containers that have started, stop all messages from being published late
		NotifyCenter.stopDeferPublish();
		GlobalExecutor.scheduleByCommon(this.infoReportTask, 5_000L);
		ApplicationUtils.setPort(event.getWebServer().getPort());
		ApplicationUtils.setLocalAddress(this.localAddress);
		Loggers.CLUSTER.info("This node is ready to provide external services");
	}

	@PreDestroy
	public void shutdown() throws NacosException {
		getSelf().setState(NodeState.DOWN);
		infoReportTask.shutdown();
		LookupFactory.destroy();
	}

	public Set<String> getMemberAddressInfos() {
		return memberAddressInfos;
	}

	@JustForTest
	public void updateMember(Member member) {
		writeLock.lock();
		try {
			serverList.put(member.getAddress(), member);
		}
		finally {
			writeLock.unlock();
		}
	}

	@JustForTest
	public void setMemberAddressInfos(Set<String> memberAddressInfos) {
		this.memberAddressInfos = memberAddressInfos;
	}

	public Map<String, Member> getServerList() {
		return Collections.unmodifiableMap(serverList);
	}

	public boolean isInIpList() {
		return isInIpList;
	}

	// Synchronize the metadata information of a node
	// A health check of the target node is also attached

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
										MemberUtils.onSuccess(MemberUtils
												.singleParse(target.getAddress()));
									}
									else {
										Loggers.CLUSTER
												.warn("failed to report new info to target node : {}, result : {}",
														target, result);
										MemberUtils.onFail(MemberUtils
												.singleParse(target.getAddress()));
									}
								}

								@Override
								public void onError(Throwable throwable) {
									Loggers.CLUSTER
											.error("failed to report new info to target node : {}, error : {}",
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
