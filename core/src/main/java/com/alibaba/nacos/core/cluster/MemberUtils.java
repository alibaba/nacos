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

import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberUtils {

	private static final String SEMICOLON = ":";

	public static void copy(Member newMember, Member oldMember) {
		oldMember.getExtendInfo().putAll(newMember.getExtendInfo());
	}

	public static Member parse(String host, int port) {
		Member member = new Member();
		member.setIp(host);
		member.setPort(port);
		return member;
	}

	public static Member parse(String address) {
		Member member = new Member();
		if (address.contains(SEMICOLON)) {
			String[] s = address.split(SEMICOLON);
			member.setIp(s[0].trim());
			member.setPort(Integer.parseInt(s[1].trim()));
		}
		else {
			member.setIp(address);
			member.setPort(8848);
		}
		return member;
	}

	public static void onSuccess(Member member, ServerMemberManager manager) {
		manager.getMemberAddressInfos().add(member.getAddress());
		member.setState(NodeState.UP);
		member.setFailAccessCnt(0);
	}

	@SuppressWarnings("PMD.UndefineMagicConstantRule")
	public static void onFail(Member member, ServerMemberManager manager) {
		manager.getMemberAddressInfos().remove(member.getAddress());
		member.setState(NodeState.SUSPICIOUS);
		member.setFailAccessCnt(member.getFailAccessCnt() + 1);
		if (member.getFailAccessCnt() > 3) {
			member.setState(NodeState.DOWN);
		}
	}

	public static Collection<Member> stringToMembers(Collection<String> addresses) {
		List<Member> members = new ArrayList<>(addresses.size());
		for (String address : addresses) {
			Member member = parse(address);
			members.add(member);
		}
		return members;
	}

	public static void syncToFile(Collection<Member> members) {
		try {
			StringBuilder builder = new StringBuilder();
			for (String member : simpleMembers(members)) {
				builder.append(member).append(StringUtils.LF);
			}
			ApplicationUtils.writeClusterConf(builder.toString());
		} catch (Throwable ex) {
			Loggers.CLUSTER.error("Cluster member node persistence failed : {}", ex);
		}
	}

	@SuppressWarnings("PMD.UndefineMagicConstantRule")
	public static List<Member> kRandom(ServerMemberManager memberManager,
			Predicate<Member> filter) {
		int k = ApplicationUtils
				.getProperty("nacos.core.member.report.random-num", Integer.class, 3);

		List<Member> members = new ArrayList<>();
		Collection<Member> have = memberManager.allMembers();

		// Here thinking similar consul gossip protocols random k node

		int totalSize = have.size();
		for (int i = 0; i < 3 * totalSize && members.size() <= k; i++) {
			for (Member member : have) {

				if (filter.test(member)) {
					members.add(member);
				}

			}
		}

		return members;
	}

	// 默认配置格式解析，只有nacos-server的ip:port or hostname:port 信息
	// example 192.168.16.1:8848?raft_port=8849&key=value

	public static void readServerConf(Collection<String> members,
			ServerMemberManager memberManager) {
		Set<Member> nodes = new HashSet<>();
		int selfPort = memberManager.getPort();

		// Nacos default port is 8848

		int defaultPort = 8848;

		// Set the default Raft port information for security

		int defaultRaftPort = selfPort + 1000 >= 65535 ? selfPort + 1 : selfPort + 1000;

		for (String member : members) {
			String[] memberDetails = member.split("\\?");
			String address = memberDetails[0];
			int port = defaultPort;
			if (address.contains(":")) {
				String[] info = address.split(":");
				address = info[0];
				port = Integer.parseInt(info[1]);
			}

			// example ip:port?raft_port=&node_name=

			Map<String, String> extendInfo = new HashMap<>(4);

			if (memberDetails.length == 2) {
				String[] parameters = memberDetails[1].split("&");
				for (String parameter : parameters) {
					String[] info = parameter.split("=");
					extendInfo.put(info[0].trim(), info[1].trim());
				}
			}
			else {

				// The Raft Port information needs to be set by default
				extendInfo.put(MemberMetaDataConstants.RAFT_PORT,
						String.valueOf(defaultRaftPort));

			}

			nodes.add(Member.builder().ip(address).port(port).extendInfo(extendInfo)
					.state(NodeState.UP).build());

		}

		memberManager.memberJoin(nodes);
	}

	public static List<String> simpleMembers(Collection<Member> members) {
		return members.stream().map(member -> {
			String address = member.getAddress();
			StringBuilder params = new StringBuilder();
			String[] keys = MemberMetaDataConstants.META_KEY_LIST;
			int length = keys.length;
			for (int i = 0; i < length; i++) {
				params.append(keys[i]).append("=").append(member.getExtendVal(keys[i]));
				if (i != length - 1) {
					params.append("&");
				}
			}
			return address + "?" + params.toString();
		}).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
	}

}
