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

import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
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
	private static final String TARGET_MEMBER_CONNECT_REFUSE_ERRMSG = "Connection refused";

	private static ServerMemberManager manager;

	public static void setManager(ServerMemberManager manager) {
		MemberUtils.manager = manager;
	}

	public static void copy(Member newMember, Member oldMember) {
		oldMember.setIp(newMember.getIp());
		oldMember.setPort(newMember.getPort());
		oldMember.setState(newMember.getState());
		oldMember.setExtendInfo(newMember.getExtendInfo());
		oldMember.setAddress(newMember.getAddress());
	}

	@SuppressWarnings("PMD.UndefineMagicConstantRule")
	public static Member singleParse(String member) {
		// Nacos default port is 8848
		int defaultPort = 8848;
		// Set the default Raft port information for securit

		String address = member;
		int port = defaultPort;
		if (address.contains(SEMICOLON)) {
			String[] info = address.split(SEMICOLON);
			address = info[0];
			port = Integer.parseInt(info[1]);
		}

		Member target = Member.builder().ip(address).port(port)
				.state(NodeState.UP).build();

		Map<String, Object> extendInfo = new HashMap<>(4);
		// The Raft Port information needs to be set by default
		extendInfo.put(MemberMetaDataConstants.RAFT_PORT, String.valueOf(calculateRaftPort(target)));
		target.setExtendInfo(extendInfo);
		return target;
	}

	public static int calculateRaftPort(Member member) {
		return member.getPort() - 1000;
	}

	public static Collection<Member> multiParse(Collection<String> addresses) {
		List<Member> members = new ArrayList<>(addresses.size());
		for (String address : addresses) {
			Member member = singleParse(address);
			members.add(member);
		}
		return members;
	}

	public static void onSuccess(Member member) {
		manager.getMemberAddressInfos().add(member.getAddress());
		member.setState(NodeState.UP);
		member.setFailAccessCnt(0);
		manager.update(member);
	}

	public static void onFail(Member member) {
		onFail(member, null);
	}

	public static void onFail(Member member, Throwable ex) {
		manager.getMemberAddressInfos().remove(member.getAddress());
		member.setState(NodeState.SUSPICIOUS);
		member.setFailAccessCnt(member.getFailAccessCnt() + 1);
		int maxFailAccessCnt = ApplicationUtils
				.getProperty("nacos.core.member.fail-access-cnt", Integer.class, 3);

		// If the number of consecutive failures to access the target node reaches
		// a maximum, or the link request is rejected, the state is directly down
		if (member.getFailAccessCnt() > maxFailAccessCnt || StringUtils.containsIgnoreCase(ex.getMessage(), TARGET_MEMBER_CONNECT_REFUSE_ERRMSG)) {
			member.setState(NodeState.DOWN);
		}
		manager.update(member);
	}

	public static void syncToFile(Collection<Member> members) {
		try {
			StringBuilder builder = new StringBuilder();
			builder.append("#").append(LocalDateTime.now()).append(StringUtils.LF);
			for (String member : simpleMembers(members)) {
				builder.append(member).append(StringUtils.LF);
			}
			ApplicationUtils.writeClusterConf(builder.toString());
		}
		catch (Throwable ex) {
			Loggers.CLUSTER.error("cluster member node persistence failed : {}",
					ExceptionUtil.getAllExceptionMsg(ex));
		}
	}

	@SuppressWarnings("PMD.UndefineMagicConstantRule")
	public static Collection<Member> kRandom(Collection<Member> members,
			Predicate<Member> filter, int k) {

        Set<Member> kMembers = new HashSet<>();

        // Here thinking similar consul gossip protocols random k node
        int totalSize = members.size();
        Member[] membersArray = members.toArray(new Member[totalSize]);
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        for (int i = 0; i < 3 * totalSize && kMembers.size() < k; i++) {
            int idx = threadLocalRandom.nextInt(totalSize);
            Member member = membersArray[idx];
            if (filter.test(member)) {
                kMembers.add(member);
            }
        }

        return kMembers;
	}

	// 默认配置格式解析，只有nacos-server的ip or ip:port or hostname:port 信息

	public static Collection<Member> readServerConf(Collection<String> members) {
		Set<Member> nodes = new HashSet<>();

		for (String member : members) {
			Member target = singleParse(member);
			nodes.add(target);
		}

		return nodes;
	}

	public static List<String> simpleMembers(Collection<Member> members) {
		return members.stream().map(Member::getAddress)
				.sorted()
				.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
	}

}
