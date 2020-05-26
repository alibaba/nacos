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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeEvent;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.ClassUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Conformance protocol management, responsible for managing the lifecycle
 * of conformance protocols in Nacos
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Component(value = "ProtocolManager")
@DependsOn("serverMemberManager")
public class ProtocolManager
		implements ApplicationListener<ContextStartedEvent>, DisposableBean,
		MemberChangeListener {

	private CPProtocol cpProtocol;
	private APProtocol apProtocol;

	@Autowired
	private ServerMemberManager memberManager;

	private boolean apInit = false;
	private boolean cpInit = false;

	@PostConstruct
	public void init() {
		this.memberManager = memberManager;
		NotifyCenter.registerSubscribe(this);
	}

	// delay init protocol

	public CPProtocol getCpProtocol() {
		synchronized (this) {
			if (!cpInit) {
				initCPProtocol();
                cpInit = true;
            }
		}
		return cpProtocol;
	}

	public APProtocol getApProtocol() {
		synchronized (this) {
			if (!apInit) {
				initAPProtocol();
                apInit = true;
            }
		}
		return apProtocol;
	}

	public void destroy() {
		if (Objects.nonNull(apProtocol)) {
			apProtocol.shutdown();
		}
		if (Objects.nonNull(cpProtocol)) {
			cpProtocol.shutdown();
		}
	}

	@Override
	public void onApplicationEvent(ContextStartedEvent event) {
	}

	private void initAPProtocol() {
		ApplicationUtils.getBeanIfExist(APProtocol.class, protocol -> {
			Class configType = ClassUtils.resolveGenericType(protocol.getClass());
			Config config = (Config) ApplicationUtils.getBean(configType);
			injectMembers4AP(config);
			protocol.init((config));
			ProtocolManager.this.apProtocol = protocol;
		});
	}

	private void initCPProtocol() {
		ApplicationUtils.getBeanIfExist(CPProtocol.class, protocol -> {
			Class configType = ClassUtils.resolveGenericType(protocol.getClass());
			Config config = (Config) ApplicationUtils.getBean(configType);
			injectMembers4CP(config);
			protocol.init((config));
			ProtocolManager.this.cpProtocol = protocol;
		});
	}

	private void injectMembers4CP(Config config) {
		final Member selfMember = memberManager.getSelf();
		final String self = selfMember.getIp() + ":" + Integer.parseInt(String.valueOf(
				selfMember.getExtendVal(MemberMetaDataConstants.RAFT_PORT)));
		Set<String> others = toCPMembersInfo(memberManager.allMembers());
		config.setMembers(self, others);
	}

	private void injectMembers4AP(Config config) {
		final String self = memberManager.getSelf().getAddress();
		Set<String> others = toAPMembersInfo(memberManager.allMembers());
		config.setMembers(self, others);
	}

	@Override
	public void onEvent(MemberChangeEvent event) {
		// Here, the sequence of node change events is very important. For example,
		// node change event A occurs at time T1, and node change event B occurs at
		// time T2 after a period of time.
		// (T1 < T2)

		Set<Member> copy = new HashSet<>(event.getMembers());

		// Node change events between different protocols should not block each other.
		// and we use a single thread pool to inform the consistency layer of node changes,
		// to avoid multiple tasks simultaneously carrying out the consistency layer of
		// node changes operation
		if (Objects.nonNull(apProtocol)) {
			ProtocolExecutor.apMemberChange(() -> apProtocol.memberChange(toAPMembersInfo(copy)));
		}
		if (Objects.nonNull(cpProtocol)) {
			ProtocolExecutor.cpMemberChange(() -> cpProtocol.memberChange(toCPMembersInfo(copy)));
		}
	}

	private static Set<String> toAPMembersInfo(Collection<Member> members) {
		Set<String> nodes = new HashSet<>();
		members.forEach(member -> nodes.add(member.getAddress()));
		return nodes;
	}

	private static Set<String> toCPMembersInfo(Collection<Member> members) {
		Set<String> nodes = new HashSet<>();
		members.forEach(member -> {
			final String ip = member.getIp();
			final int raftPort = MemberUtils.calculateRaftPort(member);
			nodes.add(ip + ":" + raftPort);
		});
		return nodes;
	}
}
