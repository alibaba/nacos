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

import com.alibaba.nacos.core.cluster.Member;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ServerUtils {

	public static Server memberToServer(Member member) {
		return Server.builder()
				.ip(member.getIp())
				.servePort(member.getPort())
				.alive(true)
				.lastRefTime(System.currentTimeMillis())
				.build();
	}

	public static Member serverToMember(Server server) {
		return Member.builder()
				.ip(server.getIp())
				.port(server.getServePort())
				.build();
	}

	public static List<Member> toMembers(Collection<Server> servers) {
		return servers.stream().map(ServerUtils::serverToMember).collect(Collectors.toList());
	}

	public static List<Server> toServers(Collection<Member> members) {
		return members.stream().map(ServerUtils::memberToServer).collect(Collectors.toList());
	}

}
