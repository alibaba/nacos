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
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.NodeChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Component(value = "CAPProtocol")
public class ProtocolManager implements ApplicationListener<ContextStartedEvent>, DisposableBean, MemberChangeListener {

	private CPProtocol cpProtocol;
	private APProtocol apProtocol;

	private ServerMemberManager memberManager;

	@VisibleForTesting
	public void setMemberManager(ServerMemberManager serverMemberManager) {
		this.memberManager = serverMemberManager;
	}

	public void init(ServerMemberManager memberManager) {

		this.memberManager = memberManager;

		// Consistency protocol module initialization

		initAPProtocol();
		initCPProtocol();
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
		stopDeferPublish();
	}

	public void stopDeferPublish() {
		if (Objects.nonNull(apProtocol)) {
			apProtocol.protocolMetaData().stopDeferPublish();
		}
		if (Objects.nonNull(cpProtocol)) {
			cpProtocol.protocolMetaData().stopDeferPublish();
		}
	}

	private void initAPProtocol() {
		ApplicationUtils.getBeanIfExist(APProtocol.class, protocol -> {
			Class configType = ClassUtils.resolveGenericType(protocol.getClass());
			Config config = (Config) ApplicationUtils.getBean(configType);
			injectMembers4AP(config);
			config.addLogProcessors(loadProcessorAndInjectProtocol(LogProcessor4AP.class, protocol));
			protocol.init((config));
			ProtocolManager.this.apProtocol = protocol;
		});
	}

	private void initCPProtocol() {
		ApplicationUtils.getBeanIfExist(CPProtocol.class, protocol -> {
			Class configType = ClassUtils.resolveGenericType(protocol.getClass());
			Config config = (Config) ApplicationUtils.getBean(configType);
			injectMembers4CP(config);
			config.addLogProcessors(loadProcessorAndInjectProtocol(LogProcessor4CP.class, protocol));
			protocol.init((config));
			ProtocolManager.this.cpProtocol = protocol;
		});
	}

	private void injectMembers4CP(Config config) {
		final Member selfMember = memberManager.getSelf();
		final String self = selfMember.getIp() + ":" + Integer.parseInt(String.valueOf(selfMember.getExtendVal(
				MemberMetaDataConstants.RAFT_PORT)));
		Set<String> others = MemberUtils.toCPMembersInfo(memberManager.allMembers());
		config.setMembers(self, others);
	}

	private void injectMembers4AP(Config config) {
		final String self = memberManager.getSelf().getAddress();
		Set<String> others = MemberUtils.toAPMembersInfo(memberManager.allMembers());
		config.setMembers(self, others);
	}

	@SuppressWarnings("all")
	private List<LogProcessor> loadProcessorAndInjectProtocol(Class cls, ConsistencyProtocol protocol) {
		Map<String, LogProcessor> beans = (Map<String, LogProcessor>) ApplicationUtils.getBeansOfType(cls);

		final List<LogProcessor> result = new ArrayList<>(beans.values());

		ServiceLoader<LogProcessor> loader = ServiceLoader.load(cls);
		for (LogProcessor t : loader) {
			result.add(t);
		}

		for (LogProcessor processor : result) {
			processor.injectProtocol(protocol);
		}

		return result;
	}

	@Override
	public void onEvent(NodeChangeEvent event) {
		Collection<Member> members = event.getAllMembers();
		if (event.getJoin()) {
			GlobalExecutor.executeByCommon(() -> apProtocol.addMembers(MemberUtils.toAPMembersInfo(members)));
			GlobalExecutor.executeByCommon(() -> cpProtocol.addMembers(MemberUtils.toCPMembersInfo(members)));
		} else {
			GlobalExecutor.executeByCommon(() -> apProtocol.removeMembers(MemberUtils.toAPMembersInfo(members)));
			GlobalExecutor.executeByCommon(() -> cpProtocol.removeMembers(MemberUtils.toCPMembersInfo(members)));
		}
	}
}
