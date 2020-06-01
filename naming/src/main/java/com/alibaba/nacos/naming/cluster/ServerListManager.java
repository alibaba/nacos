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

package com.alibaba.nacos.naming.cluster;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.MemberChangeEvent;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Message;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.ServerStatusSynchronizer;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.Synchronizer;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The manager to globally refresh and operate server list.
 *
 * // TODO This object will be deleted sometime after version 1.3.0
 *
 * @author nkorange
 * @since 1.0.0
 * @deprecated 1.3.0
 */
@Component("serverListManager")
public class ServerListManager implements MemberChangeListener {

	private static final int STABLE_PERIOD = 60 * 1000;

	private final SwitchDomain switchDomain;
	private final ServerMemberManager memberManager;

	private volatile List<Server> servers;

	private volatile List<Server> healthyServers = Collections.emptyList();

	private Map<String, List<Server>> distroConfig = new ConcurrentHashMap<>(16);

	private Map<String, Long> distroBeats = new ConcurrentHashMap<>(16);

	private Set<String> liveSites = new HashSet<>();

	private final static String LOCALHOST_SITE = UtilsAndCommons.UNKNOWN_SITE;

	private long lastHealthServerMillis = 0L;

	private boolean autoDisabledHealthCheck = false;

	private Synchronizer synchronizer = new ServerStatusSynchronizer();

	public ServerListManager(final SwitchDomain switchDomain,
			final ServerMemberManager memberManager) {
		this.switchDomain = switchDomain;
		this.memberManager = memberManager;
		NotifyCenter.registerSubscribe(this);
		this.servers = ServerUtils.toServers(memberManager.allMembers());
	}

	@PostConstruct
	public void init() {
		GlobalExecutor.registerServerStatusReporter(new ServerStatusReporter(), 2000);
	}

	public boolean contains(String s) {
		for (Server server : getServers()) {
			if (server.getKey().equals(s)) {
				return true;
			}
		}
		return false;
	}

	public List<Server> getServers() {
		return servers;
	}

	public synchronized void onReceiveServerStatus(String configInfo) {

		Loggers.SRV_LOG.info("receive config info: {}", configInfo);

		String[] configs = configInfo.split("\r\n");
		if (configs.length == 0) {
			return;
		}

		List<Server> newHealthyList = new ArrayList<>();
		List<Server> tmpServerList = new ArrayList<>();

		for (String config : configs) {
			tmpServerList.clear();
			// site:ip:lastReportTime:weight
			String[] params = config.split("#");
			if (params.length <= 3) {
				Loggers.SRV_LOG.warn("received malformed distro map data: {}", config);
				continue;
			}

			Server server = new Server();

			server.setSite(params[0]);
			server.setIp(params[1].split(UtilsAndCommons.IP_PORT_SPLITER)[0]);
			server.setServePort(Integer.parseInt(params[1].split(UtilsAndCommons.IP_PORT_SPLITER)[1]));
			server.setLastRefTime(Long.parseLong(params[2]));

			if (!contains(server.getKey())) {
				throw new IllegalArgumentException("server: " + server.getKey() + " is not in serverlist");
			}

			Long lastBeat = distroBeats.get(server.getKey());
			long now = System.currentTimeMillis();
			if (null != lastBeat) {
				server.setAlive(now - lastBeat < switchDomain.getDistroServerExpiredMillis());
			}
			distroBeats.put(server.getKey(), now);

			Date date = new Date(Long.parseLong(params[2]));
			server.setLastRefTimeStr(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));

			server.setWeight(params.length == 4 ? Integer.parseInt(params[3]) : 1);
			List<Server> list = distroConfig.get(server.getSite());
			if (list == null || list.size() <= 0) {
				list = new ArrayList<>();
				list.add(server);
				distroConfig.put(server.getSite(), list);
			}

			for (Server s : list) {
				String serverId = s.getKey() + "_" + s.getSite();
				String newServerId = server.getKey() + "_" + server.getSite();

				if (serverId.equals(newServerId)) {
					if (s.isAlive() != server.isAlive() || s.getWeight() != server.getWeight()) {
						Loggers.SRV_LOG.warn("server beat out of date, current: {}, last: {}",
								JacksonUtils.toJson(server), JacksonUtils.toJson(s));
					}
					tmpServerList.add(server);
					continue;
				}
				tmpServerList.add(s);
			}

			if (!tmpServerList.contains(server)) {
				tmpServerList.add(server);
			}

			distroConfig.put(server.getSite(), tmpServerList);
		}
		liveSites.addAll(distroConfig.keySet());
	}

	public void clean() {
		cleanInvalidServers();

		for (Map.Entry<String, List<Server>> entry : distroConfig.entrySet()) {
			for (Server server : entry.getValue()) {
				//request other server to clean invalid servers
				if (!server.getKey().equals(NetUtils.localServer())) {
					requestOtherServerCleanInvalidServers(server.getKey());
				}
			}

		}
	}

	public Set<String> getLiveSites() {
		return liveSites;
	}

	private void cleanInvalidServers() {
		for (Map.Entry<String, List<Server>> entry : distroConfig.entrySet()) {
			List<Server> currentServers = entry.getValue();
			if (null == currentServers) {
				distroConfig.remove(entry.getKey());
				continue;
			}

			currentServers.removeIf(server -> !server.isAlive());
		}
	}

	private void requestOtherServerCleanInvalidServers(String serverIP) {
		Map<String, String> params = new HashMap<String, String>(1);

		params.put("action", "without-diamond-clean");
		try {
			NamingProxy.reqAPI("distroStatus", params, serverIP, false);
		} catch (Exception e) {
			Loggers.SRV_LOG.warn("[DISTRO-STATUS-CLEAN] Failed to request to clean server status to " + serverIP, e);
		}
	}

	@Override
	public void onEvent(MemberChangeEvent event) {
		this.servers = ServerUtils.toServers(memberManager.allMembers());
	}

	private class ServerStatusReporter implements Runnable {

		@Override
		public void run() {
			try {

				if (ApplicationUtils.getPort() <= 0) {
					return;
				}

				checkDistroHeartbeat();

				int weight = Runtime.getRuntime().availableProcessors() / 2;
				if (weight <= 0) {
					weight = 1;
				}

				long curTime = System.currentTimeMillis();
				String status = LOCALHOST_SITE + "#" + NetUtils.localServer() + "#" + curTime + "#" + weight + "\r\n";

				//send status to itself
				onReceiveServerStatus(status);

				List<Server> allServers = getServers();

				if (!contains(NetUtils.localServer())) {
					Loggers.SRV_LOG.error("local ip is not in serverlist, ip: {}, serverlist: {}", NetUtils.localServer(), allServers);
					return;
				}

				if (allServers.size() > 0 && !ApplicationUtils.getLocalAddress().contains(UtilsAndCommons.LOCAL_HOST_IP)) {
					for (Server server : allServers) {
						if (server.getKey().equals(ApplicationUtils.getLocalAddress())) {
							continue;
						}

						Message msg = new Message();
						msg.setData(status);

						synchronizer.send(server.getKey(), msg);

					}
				}
			} catch (Exception e) {
				Loggers.SRV_LOG.error("[SERVER-STATUS] Exception while sending server status", e);
			} finally {
				GlobalExecutor.registerServerStatusReporter(this, switchDomain.getServerStatusSynchronizationPeriodMillis());
			}

		}
	}

	private void checkDistroHeartbeat() {

		Loggers.SRV_LOG.debug("check distro heartbeat.");

		List<Server> servers = distroConfig.get(LOCALHOST_SITE);
		if (CollectionUtils.isEmpty(servers)) {
			return;
		}

		List<Server> newHealthyList = new ArrayList<>(servers.size());
		long now = System.currentTimeMillis();
		for (Server s: servers) {
			Long lastBeat = distroBeats.get(s.getKey());
			if (null == lastBeat) {
				continue;
			}
			s.setAlive(now - lastBeat < switchDomain.getDistroServerExpiredMillis());
		}

		//local site servers
		List<String> allLocalSiteSrvs = new ArrayList<>();
		for (Server server : servers) {

			if (server.getKey().endsWith(":0")) {
				continue;
			}

			server.setAdWeight(switchDomain.getAdWeight(server.getKey()) == null ? 0 : switchDomain.getAdWeight(server.getKey()));

			for (int i = 0; i < server.getWeight() + server.getAdWeight(); i++) {

				if (!allLocalSiteSrvs.contains(server.getKey())) {
					allLocalSiteSrvs.add(server.getKey());
				}

				if (server.isAlive() && !newHealthyList.contains(server)) {
					newHealthyList.add(server);
				}
			}
		}

		Collections.sort(newHealthyList);
		float curRatio = (float) newHealthyList.size() / allLocalSiteSrvs.size();

		if (autoDisabledHealthCheck
				&& curRatio > switchDomain.getDistroThreshold()
				&& System.currentTimeMillis() - lastHealthServerMillis > STABLE_PERIOD) {
			Loggers.SRV_LOG.info("[NACOS-DISTRO] distro threshold restored and " +
					"stable now, enable health check. current ratio: {}", curRatio);

			switchDomain.setHealthCheckEnabled(true);

			// we must set this variable, otherwise it will conflict with user's action
			autoDisabledHealthCheck = false;
		}

		if (!CollectionUtils.isEqualCollection(healthyServers, newHealthyList)) {
			// for every change disable healthy check for some while
			Loggers.SRV_LOG.info("[NACOS-DISTRO] healthy server list changed, old: {}, new: {}",
					healthyServers, newHealthyList);
			if (switchDomain.isHealthCheckEnabled() && switchDomain.isAutoChangeHealthCheckEnabled()) {
				Loggers.SRV_LOG.info("[NACOS-DISTRO] disable health check for {} ms from now on.", STABLE_PERIOD);

				switchDomain.setHealthCheckEnabled(false);
				autoDisabledHealthCheck = true;

				lastHealthServerMillis = System.currentTimeMillis();
			}

			healthyServers = newHealthyList;
		}
	}
}