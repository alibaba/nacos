/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UpgradeJudgementTest {
    
    private final long sleepForCheck = 800L;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Mock
    private RaftPeerSet raftPeerSet;
    
    @Mock
    private RaftCore raftCore;
    
    @Mock
    private ClusterVersionJudgement versionJudgement;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ServiceManager serviceManager;
    
    @Mock
    private DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    @Mock
    private UpgradeStates upgradeStates;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    private UpgradeJudgement upgradeJudgement;
    
    @Before
    public void setUp() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.SUPPORT_UPGRADE_FROM_1X, "true");
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(false);
        when(context.getBean(ServiceManager.class)).thenReturn(serviceManager);
        when(context.getBean(ServiceStorage.class)).thenReturn(serviceStorage);
        ApplicationUtils.injectContext(context);
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        NotifyCenter.deregisterSubscriber(upgradeJudgement);
        MetricsMonitor.getIpCountMonitor().set(0);
        MetricsMonitor.getDomCountMonitor().set(0);
    }
    
    @After
    public void tearDown() {
        upgradeJudgement.shutdown();
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    public void testUpgradeOneNode() throws Exception {
        Collection<Member> members = mockMember("1.3.2", "1.3.2", "2.0.0");
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testUpgradeOneFor14XNode() throws Exception {
        Collection<Member> members = mockMember("1.4.0", "2.0.0", "2.0.0");
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertTrue(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testUpgradeTwoNode() throws Exception {
        Collection<Member> members = mockMember("", "2.0.0", "2.0.0");
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testUpgradeCheckSucc() throws Exception {
        Collection<Member> members = mockMember("2.0.0-snapshot", "2.0.0", "2.0.0");
        Iterator<Member> iterator = members.iterator();
        when(doubleWriteDelayTaskEngine.isEmpty()).thenReturn(true);
        iterator.next();
        while (iterator.hasNext()) {
            iterator.next().setExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE, true);
        }
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertTrue(upgradeJudgement.isUseGrpcFeatures());
        assertTrue(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testUpgradeCheckSelfFail() throws Exception {
        Collection<Member> members = mockMember("2.0.0", "2.0.0", "2.0.0");
        Iterator<Member> iterator = members.iterator();
        when(doubleWriteDelayTaskEngine.isEmpty()).thenReturn(false);
        iterator.next();
        while (iterator.hasNext()) {
            iterator.next().setExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE, true);
        }
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testAlreadyUpgradedAndCheckSelfFail() throws Exception {
        Collection<Member> members = mockMember("2.0.0", "2.0.0", "2.0.0");
        Iterator<Member> iterator = members.iterator();
        iterator.next();
        while (iterator.hasNext()) {
            iterator.next().setExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE, true);
        }
        when(upgradeStates.isUpgraded()).thenReturn(true);
        upgradeJudgement.shutdown();
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertTrue(upgradeJudgement.isUseGrpcFeatures());
        assertTrue(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testUpgradeCheckOthersFail() throws Exception {
        Collection<Member> members = mockMember("2.0.0", "2.0.0", "2.0.0");
        when(doubleWriteDelayTaskEngine.isEmpty()).thenReturn(true);
        members.iterator().next().setExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE, true);
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testAlreadyUpgradedAndCheckOthersFail() throws Exception {
        Collection<Member> members = mockMember("2.0.0", "2.0.0", "2.0.0");
        members.iterator().next().setExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE, true);
        upgradeJudgement.shutdown();
        when(upgradeStates.isUpgraded()).thenReturn(true);
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        assertTrue(upgradeJudgement.isUseGrpcFeatures());
        assertTrue(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testDowngradeOneFor14XNode() throws Exception {
        upgradeJudgement.setUseGrpcFeatures(true);
        upgradeJudgement.setUseJraftFeatures(true);
        Collection<Member> members = mockMember("1.4.0", "2.0.0", "2.0.0");
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertTrue(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testAlreadyUpgradedAndDowngradeOneFor14XNode() throws Exception {
        when(upgradeStates.isUpgraded()).thenReturn(true);
        upgradeJudgement.shutdown();
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(mockMember("1.4.0", "2.0.0", "2.0.0")).build());
        verify(raftPeerSet, never()).init();
        verify(raftCore, never()).init();
        verify(versionJudgement, never()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertTrue(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testDowngradeTwoNode() throws Exception {
        upgradeJudgement.setUseGrpcFeatures(true);
        upgradeJudgement.setUseJraftFeatures(true);
        Collection<Member> members = mockMember("", "", "2.0.0");
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, atMostOnce()).init();
        verify(raftCore, atMostOnce()).init();
        verify(versionJudgement, atMostOnce()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testAlreadyUpgradedAndDowngradeTwoNode() throws Exception {
        when(upgradeStates.isUpgraded()).thenReturn(true);
        upgradeJudgement.shutdown();
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(mockMember("", "", "2.0.0")).build());
        verify(raftPeerSet, atMostOnce()).init();
        verify(raftCore, atMostOnce()).init();
        verify(versionJudgement, atMostOnce()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testDowngradeOneNode() throws Exception {
        upgradeJudgement.setUseGrpcFeatures(true);
        upgradeJudgement.setUseJraftFeatures(true);
        Collection<Member> members = mockMember("1.3.2", "2.0.0", "2.0.0");
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(members).build());
        verify(raftPeerSet, atMostOnce()).init();
        verify(raftCore, atMostOnce()).init();
        verify(versionJudgement, atMostOnce()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testAlreadyUpgradedAndDowngradeOneNode() throws Exception {
        when(upgradeStates.isUpgraded()).thenReturn(true);
        upgradeJudgement.shutdown();
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        upgradeJudgement.onEvent(MembersChangeEvent.builder().triggers(mockMember("1.3.2", "2.0.0", "2.0.0")).build());
        verify(raftPeerSet, atMostOnce()).init();
        verify(raftCore, atMostOnce()).init();
        verify(versionJudgement, atMostOnce()).reset();
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertFalse(upgradeJudgement.isUseGrpcFeatures());
        assertFalse(upgradeJudgement.isUseJraftFeatures());
    }
    
    @Test
    public void testUpgradedBySpecifiedSelfUpgradeChecker() throws InterruptedException {
        upgradeJudgement.shutdown();
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty("upgrading.checker.type", "mock");
        mockEnvironment.setProperty(Constants.SUPPORT_UPGRADE_FROM_1X, "true");
        EnvUtil.setEnvironment(mockEnvironment);
        mockMember("1.3.2", "2.0.0", "2.0.0");
        upgradeJudgement = new UpgradeJudgement(raftPeerSet, raftCore, versionJudgement, memberManager, serviceManager,
                upgradeStates, doubleWriteDelayTaskEngine);
        TimeUnit.MILLISECONDS.sleep(sleepForCheck);
        assertTrue((Boolean) memberManager.getSelf().getExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE));
    }
    
    private Collection<Member> mockMember(String... versions) {
        Collection<Member> result = new HashSet<>();
        for (int i = 0; i < versions.length; i++) {
            Member member = new Member();
            member.setPort(i);
            if (StringUtils.isNotBlank(versions[i])) {
                member.setExtendVal(MemberMetaDataConstants.VERSION, versions[i]);
            }
            result.add(member);
        }
        when(memberManager.getSelf()).thenReturn(result.iterator().next());
        when(memberManager.allMembers()).thenReturn(result);
        return result;
    }
}
