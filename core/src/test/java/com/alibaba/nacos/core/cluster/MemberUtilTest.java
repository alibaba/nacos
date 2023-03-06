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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MemberUtilTest {
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 8848;
    
    @Mock
    private ServerMemberManager memberManager;
    
    private ConfigurableEnvironment environment;
    
    private Member originalMember;
    
    private Set<String> mockMemberAddressInfos;
    
    private String nacosHome;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(true);
        nacosHome = EnvUtil.getNacosHome();
        EnvUtil.setNacosHomePath(nacosHome + File.separator + "MemberUtilTest");
        originalMember = buildMember();
        mockMemberAddressInfos = new HashSet<>();
        when(memberManager.getMemberAddressInfos()).thenReturn(mockMemberAddressInfos);
    }
    
    private Member buildMember() {
        return Member.builder().ip(IP).port(PORT).state(NodeState.UP).build();
    }
    
    @After
    public void tearDown() throws NacosException {
        EnvUtil.setNacosHomePath(nacosHome);
    }
    
    @Test
    public void testCopy() {
        Member expected = Member.builder().build();
        expected.setIp("2.2.2.2");
        expected.setPort(9999);
        expected.setState(NodeState.SUSPICIOUS);
        expected.setExtendVal(MemberMetaDataConstants.VERSION, "test");
        expected.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        MemberUtil.copy(expected, originalMember);
        assertEquals(expected.getIp(), originalMember.getIp());
        assertEquals(expected.getPort(), originalMember.getPort());
        assertEquals(expected.getAddress(), originalMember.getAddress());
        assertEquals(NodeState.SUSPICIOUS, originalMember.getState());
        assertEquals("test", originalMember.getExtendVal(MemberMetaDataConstants.VERSION));
        assertTrue(originalMember.getAbilities().getRemoteAbility().isSupportRemoteConnection());
    }
    
    @Test
    public void testSingleParseWithPort() {
        Member actual = MemberUtil.singleParse(IP + ":2222");
        assertEquals(IP, actual.getIp());
        assertEquals(2222, actual.getPort());
        assertEquals(IP + ":2222", actual.getAddress());
        assertEquals(NodeState.UP, actual.getState());
        assertEquals(true, actual.getExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE));
        assertEquals("1222", actual.getExtendVal(MemberMetaDataConstants.RAFT_PORT));
        assertFalse(actual.getAbilities().getRemoteAbility().isSupportRemoteConnection());
    }
    
    @Test
    public void testSingleParseWithoutPort() {
        Member actual = MemberUtil.singleParse(IP);
        assertEquals(IP, actual.getIp());
        assertEquals(PORT, actual.getPort());
        assertEquals(IP + ":" + PORT, actual.getAddress());
        assertEquals(NodeState.UP, actual.getState());
        assertEquals(true, actual.getExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE));
        assertEquals("7848", actual.getExtendVal(MemberMetaDataConstants.RAFT_PORT));
        assertFalse(actual.getAbilities().getRemoteAbility().isSupportRemoteConnection());
    }
    
    @Test
    public void testIsSupportedLongCon() {
        assertFalse(MemberUtil.isSupportedLongCon(originalMember));
        originalMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        assertTrue(MemberUtil.isSupportedLongCon(originalMember));
        originalMember.getAbilities().setRemoteAbility(null);
        assertFalse(MemberUtil.isSupportedLongCon(originalMember));
        originalMember.setAbilities(null);
        assertFalse(MemberUtil.isSupportedLongCon(originalMember));
    }
    
    @Test
    public void testMultiParse() {
        Collection<String> address = new HashSet<>();
        address.add("1.1.1.1:3306");
        address.add("1.1.1.1");
        Collection<Member> actual = MemberUtil.multiParse(address);
        assertEquals(2, actual.size());
    }
    
    @Test
    public void testSyncToFile() throws IOException {
        File file = new File(EnvUtil.getClusterConfFilePath());
        file.getParentFile().mkdirs();
        assertTrue(file.createNewFile());
        MemberUtil.syncToFile(Collections.singleton(originalMember));
        try (BufferedReader reader = new BufferedReader(new FileReader(EnvUtil.getClusterConfFilePath()))) {
            String line = "";
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    assertEquals(IP + ":" + PORT, line.trim());
                    return;
                }
            }
            Assert.fail("No found member info in cluster.conf");
        } finally {
            file.delete();
        }
    }
    
    @Test
    public void testReadServerConf() {
        Collection<String> address = new HashSet<>();
        address.add("1.1.1.1:3306");
        address.add("1.1.1.1");
        Collection<Member> actual = MemberUtil.readServerConf(address);
        assertEquals(2, actual.size());
    }
    
    @Test
    public void testSelectTargetMembers() {
        Collection<Member> input = new HashSet<>();
        input.add(originalMember);
        Member member = buildMember();
        member.setIp("2.2.2.2");
        input.add(member);
        Set<Member> actual = MemberUtil.selectTargetMembers(input, member1 -> member1.getIp().equals(IP));
        assertEquals(1, actual.size());
    }
    
    @Test
    public void testIsBasicInfoChangedNoChangeWithoutExtendInfo() {
        Member newMember = buildMember();
        assertFalse(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedNoChangeWithExtendInfo() {
        Member newMember = buildMember();
        newMember.setExtendVal("test", "test");
        assertFalse(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForIp() {
        Member newMember = buildMember();
        newMember.setIp("1.1.1.2");
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForPort() {
        Member newMember = buildMember();
        newMember.setPort(PORT + 1);
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForAddress() {
        Member newMember = buildMember();
        newMember.setAddress("test");
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForStatus() {
        Member newMember = buildMember();
        newMember.setState(NodeState.DOWN);
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForMoreBasicExtendInfo() {
        Member newMember = buildMember();
        newMember.setExtendVal(MemberMetaDataConstants.VERSION, "TEST");
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForChangedBasicExtendInfo() {
        Member newMember = buildMember();
        newMember.setExtendVal(MemberMetaDataConstants.WEIGHT, "100");
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForChangedAbilities() {
        Member newMember = buildMember();
        newMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForChangedNull() {
        Member newMember = buildMember();
        assertTrue(MemberUtil.isBasicInfoChanged(newMember, null));
    }
    
    @Test
    public void testMemberOnFailWhenReachMaxFailAccessCnt() {
        final Member remote = buildMember();
        mockMemberAddressInfos.add(remote.getAddress());
        remote.setState(NodeState.SUSPICIOUS);
        remote.setFailAccessCnt(2);
        MemberUtil.onFail(memberManager, remote);
        Assert.assertEquals(3, remote.getFailAccessCnt());
        Assert.assertEquals(NodeState.SUSPICIOUS, remote.getState());
        verify(memberManager, never()).notifyMemberChange(remote);
        Assert.assertTrue(mockMemberAddressInfos.isEmpty());
        MemberUtil.onFail(memberManager, remote);
        Assert.assertEquals(4, remote.getFailAccessCnt());
        Assert.assertEquals(NodeState.DOWN, remote.getState());
        verify(memberManager).notifyMemberChange(remote);
    }
    
    @Test
    public void testMemberOnFailWhenConnectRefused() {
        final Member remote = buildMember();
        mockMemberAddressInfos.add(remote.getAddress());
        remote.setFailAccessCnt(1);
        MemberUtil.onFail(memberManager, remote, new ConnectException(MemberUtil.TARGET_MEMBER_CONNECT_REFUSE_ERRMSG));
        Assert.assertEquals(2, remote.getFailAccessCnt());
        Assert.assertEquals(NodeState.DOWN, remote.getState());
        Assert.assertTrue(mockMemberAddressInfos.isEmpty());
        verify(memberManager).notifyMemberChange(remote);
    }
    
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    @Test
    public void testMemberOnFailWhenMemberAlreadyNOUP() {
        final Member remote = buildMember();
        remote.setState(NodeState.DOWN);
        remote.setFailAccessCnt(4);
        MemberUtil.onFail(memberManager, remote);
        verify(memberManager, never()).notifyMemberChange(remote);
    }
    
    @Test
    public void testMemberOnSuccessFromDown() {
        final Member remote = buildMember();
        remote.setState(NodeState.DOWN);
        remote.setFailAccessCnt(4);
        MemberUtil.onSuccess(memberManager, remote);
        assertEquals(NodeState.UP, remote.getState());
        assertEquals(0, remote.getFailAccessCnt());
        verify(memberManager).notifyMemberChange(remote);
    }
    
    @Test
    public void testMemberOnSuccessWhenMemberAlreadyUP() {
        final Member remote = buildMember();
        memberManager.updateMember(remote);
        MemberUtil.onSuccess(memberManager, remote);
        verify(memberManager, never()).notifyMemberChange(remote);
    }
    
    @Test
    public void testMemberOnSuccessWhenMemberNotUpdated() {
        final Member remote = buildMember();
        final Member reportResult = buildMember();
        MemberUtil.onSuccess(memberManager, remote, reportResult);
        assertFalse(remote.getAbilities().getRemoteAbility().isSupportRemoteConnection());
        assertTrue(mockMemberAddressInfos.contains(remote.getAddress()));
        verify(memberManager, never()).notifyMemberChange(remote);
    }
    
    @Test
    public void testMemberOnSuccessWhenMemberUpdatedAbilities() {
        final Member remote = buildMember();
        final Member reportResult = buildMember();
        reportResult.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        MemberUtil.onSuccess(memberManager, remote, reportResult);
        assertTrue(remote.getAbilities().getRemoteAbility().isSupportRemoteConnection());
        assertTrue(mockMemberAddressInfos.contains(remote.getAddress()));
        verify(memberManager).notifyMemberChange(remote);
    }
    
    @Test
    public void testMemberOnSuccessWhenMemberUpdatedExtendInfo() {
        final Member remote = buildMember();
        final Member reportResult = buildMember();
        reportResult.setExtendVal(MemberMetaDataConstants.VERSION, "test");
        MemberUtil.onSuccess(memberManager, remote, reportResult);
        assertEquals("test", remote.getExtendVal(MemberMetaDataConstants.VERSION));
        assertTrue(mockMemberAddressInfos.contains(remote.getAddress()));
        verify(memberManager).notifyMemberChange(remote);
    }
}
