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
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeEvent;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Message;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.ServerStatusSynchronizer;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.Synchronizer;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The manager to globally refresh and operate server list.
 *
 * @author nkorange
 * @since 1.0.0
 * @deprecated 1.3.0 This object will be deleted sometime after version 1.3.0
 */
@Component("serverListManager")
public class ServerListManager implements MemberChangeListener {

	private final static String LOCALHOST_SITE = UtilsAndCommons.UNKNOWN_SITE;

	private final SwitchDomain switchDomain;
	private final ServerMemberManager memberManager;
	private final Synchronizer synchronizer = new ServerStatusSynchronizer();

	private volatile List<Member> servers;

	public ServerListManager(final SwitchDomain switchDomain,
			final ServerMemberManager memberManager) {
		this.switchDomain = switchDomain;
		this.memberManager = memberManager;
		NotifyCenter.registerSubscribe(this);
		this.servers = new ArrayList<>(memberManager.allMembers());
	}

	@PostConstruct
	public void init() {
		GlobalExecutor.registerServerStatusReporter(new ServerStatusReporter(), 2000);
		GlobalExecutor.registerServerInfoUpdater(new ServerInfoUpdater());
	}

	public boolean contains(String s) {
		for (Member server : getServers()) {
			if (Objects.equals(s, server.getAddress())) {
				return true;
			}
		}
		return false;
	}

	public List<Member> getServers() {
		return servers;
	}

	@Override
	public void onEvent(MemberChangeEvent event) {
		this.servers = new ArrayList<>(event.getMembers());
	}

	/**
	 * Compatible with older version logic, In version 1.2.1 and before
	 *
	 * @param configInfo site:ip:lastReportTime:weight
	 */
	public synchronized void onReceiveServerStatus(String configInfo) {

		Loggers.SRV_LOG.info("receive config info: {}", configInfo);

		String[] configs = configInfo.split("\r\n");
		if (configs.length == 0) {
			return;
		}

		for (String config : configs) {
			// site:ip:lastReportTime:weight
			String[] params = config.split("#");
			if (params.length <= 3) {
				Loggers.SRV_LOG.warn("received malformed distro map data: {}", config);
				continue;
			}

			Member server = Optional.ofNullable(memberManager.find(params[1])).orElse(Member.builder()
					.ip(params[1].split(UtilsAndCommons.IP_PORT_SPLITER)[0])
					.state(NodeState.UP)
					.port(Integer.parseInt(params[1].split(UtilsAndCommons.IP_PORT_SPLITER)[1]))
					.build());

			server.setExtendVal(MemberMetaDataConstants.SITE_KEY, params[0]);
			server.setExtendVal(MemberMetaDataConstants.WEIGHT, params.length == 4 ? Integer.parseInt(params[3]) : 1);
			memberManager.update(server);

			if (!contains(server.getAddress())) {
				throw new IllegalArgumentException("server: " + server.getAddress() + " is not in serverlist");
			}
		}
	}

	private class ServerInfoUpdater implements Runnable {

		private int cursor = 0;

		@Override
		public void run() {
			List<Member> members = servers;
			if (members.isEmpty()) {
				return;
			}

			this.cursor = (this.cursor + 1) % members.size();
			Member target = members.get(cursor);
			if (Objects.equals(target.getAddress(), ApplicationUtils.getLocalAddress())) {
				return;
			}

			// This metadata information exists from 1.3.0 onwards "version"
			if (target.getExtendVal(MemberMetaDataConstants.VERSION) != null) {
				return;
			}

			final String path =  UtilsAndCommons.NACOS_NAMING_OPERATOR_CONTEXT + UtilsAndCommons.NACOS_NAMING_CLUSTER_CONTEXT + "/state";
			final Map<String, String> params = Maps.newHashMapWithExpectedSize(2);
			final String server = target.getAddress();

			try {
				String content = NamingProxy.reqCommon(path, params, server, false);
				if (!StringUtils.EMPTY.equals(content)) {
					RaftPeer raftPeer = JacksonUtils.toObj(content, RaftPeer.class);
					if (null != raftPeer) {
						String json = JacksonUtils.toJson(raftPeer);
						Map map = JacksonUtils.toObj(json, HashMap.class);
						target.setExtendVal("naming", map);
						memberManager.update(target);
					}
				}
			} catch (Exception ignore) {
				//
			}
		}
	}

	private class ServerStatusReporter implements Runnable {

		@Override
		public void run() {
			try {

				if (ApplicationUtils.getPort() <= 0) {
					return;
				}

				int weight = Runtime.getRuntime().availableProcessors() / 2;
				if (weight <= 0) {
					weight = 1;
				}

				long curTime = System.currentTimeMillis();
				String status = LOCALHOST_SITE + "#" + ApplicationUtils.getLocalAddress() + "#" + curTime + "#" + weight + "\r\n";

				List<Member> allServers = getServers();

				if (!contains(ApplicationUtils.getLocalAddress())) {
					Loggers.SRV_LOG.error("local ip is not in serverlist, ip: {}, serverlist: {}", ApplicationUtils.getLocalAddress(), allServers);
					return;
				}

				if (allServers.size() > 0 && !ApplicationUtils.getLocalAddress().contains(UtilsAndCommons.LOCAL_HOST_IP)) {
					for (Member server : allServers) {
						if (Objects.equals(server.getAddress(), ApplicationUtils.getLocalAddress())) {
							continue;
						}

						// This metadata information exists from 1.3.0 onwards "version"
						if (server.getExtendVal(MemberMetaDataConstants.VERSION) != null) {
						    Loggers.SRV_LOG.debug("[SERVER-STATUS] target {} has extend val {} = {}, use new api report status", server.getAddress(), MemberMetaDataConstants.VERSION, server.getExtendVal(MemberMetaDataConstants.VERSION));
							continue;
						}

						Message msg = new Message();
						msg.setData(status);

						synchronizer.send(server.getAddress(), msg);
					}
				}
			} catch (Exception e) {
				Loggers.SRV_LOG.error("[SERVER-STATUS] Exception while sending server status", e);
			} finally {
				GlobalExecutor.registerServerStatusReporter(this, switchDomain.getServerStatusSynchronizationPeriodMillis());
			}

		}
	}

}
