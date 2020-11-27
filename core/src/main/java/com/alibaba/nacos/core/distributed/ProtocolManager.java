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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Conformance protocol management, responsible for managing the lifecycle of conformance protocols in Nacos.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class ProtocolManager extends MemberChangeListener {
    
    private CPProtocol cpProtocol;
    
    private APProtocol apProtocol;
    
    private ServerMemberManager memberManager;
    
    private boolean apInit = false;
    
    private boolean cpInit = false;
    
    private Set<Member> oldMembers;
    
    private static final ProtocolManager INSTANCE = new ProtocolManager();
    
    public static ProtocolManager getInstance() {
        return INSTANCE;
    }
    
    public static void init(ServerMemberManager memberManager) {
        INSTANCE.memberManager = memberManager;
        NotifyCenter.registerSubscriber(INSTANCE);
    }
    
    public static Set<String> toAPMembersInfo(Collection<Member> members) {
        Set<String> nodes = new HashSet<>();
        members.forEach(member -> nodes.add(member.getAddress()));
        return nodes;
    }
    
    public static Set<String> toCPMembersInfo(Collection<Member> members) {
        Set<String> nodes = new HashSet<>();
        members.forEach(member -> {
            final String ip = member.getIp();
            final int raftPort = MemberUtils.calculateRaftPort(member);
            nodes.add(ip + ":" + raftPort);
        });
        return nodes;
    }
    
    public static CPProtocol getCpProtocol() {
        synchronized (INSTANCE) {
            if (!INSTANCE.cpInit) {
                INSTANCE.initCPProtocol();
                INSTANCE.cpInit = true;
            }
        }
        return INSTANCE.cpProtocol;
    }
    
    public static  APProtocol getApProtocol() {
        synchronized (INSTANCE) {
            if (!INSTANCE.apInit) {
                INSTANCE.initAPProtocol();
                INSTANCE.apInit = true;
            }
        }
        return INSTANCE.apProtocol;
    }
    
    public static void destroy() {
        if (Objects.nonNull(INSTANCE.apProtocol)) {
            INSTANCE.apProtocol.shutdown();
        }
        if (Objects.nonNull(INSTANCE.cpProtocol)) {
            INSTANCE.cpProtocol.shutdown();
        }
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
        final String self = selfMember.getIp() + ":" + Integer
                .parseInt(String.valueOf(selfMember.getExtendVal(MemberMetaDataConstants.RAFT_PORT)));
        Set<String> others = toCPMembersInfo(memberManager.allMembers());
        config.setMembers(self, others);
    }
    
    private void injectMembers4AP(Config config) {
        final String self = memberManager.getSelf().getAddress();
        Set<String> others = toAPMembersInfo(memberManager.allMembers());
        config.setMembers(self, others);
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        // Here, the sequence of node change events is very important. For example,
        // node change event A occurs at time T1, and node change event B occurs at
        // time T2 after a period of time.
        // (T1 < T2)
        // Node change events between different protocols should not block each other.
        // and we use a single thread pool to inform the consistency layer of node changes,
        // to avoid multiple tasks simultaneously carrying out the consistency layer of
        // node changes operation
        if (Objects.nonNull(apProtocol)) {
            ProtocolExecutor.apMemberChange(() -> apProtocol.memberChange(toAPMembersInfo(event.getMembers())));
        }
        if (Objects.nonNull(cpProtocol)) {
            ProtocolExecutor.cpMemberChange(() -> cpProtocol.memberChange(toCPMembersInfo(event.getMembers())));
        }
    }
}
