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

import com.alibaba.nacos.api.ability.ServerAbilities;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.cluster.remote.response.MemberReportResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServerMemberManagerTest {
    
    private static final AtomicBoolean EVENT_PUBLISH = new AtomicBoolean(false);
    
    @Mock
    private ConfigurableEnvironment environment;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    private ServerMemberManager serverMemberManager;
    
    @BeforeEach
    void setUp() throws Exception {
        when(environment.getProperty("nacos.server.main.port", Integer.class, 8848))
            .thenReturn(8848);
        when(environment.getProperty("nacos.member-change-event.queue.size", Integer.class, 128))
            .thenReturn(128);
        ApplicationUtils.injectContext(context);
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(true);
        serverMemberManager = new ServerMemberManager();
        serverMemberManager
            .updateMember(Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build());
        serverMemberManager.getMemberAddressInfos().add("1.1.1.1:8848");
        NotifyCenter.getPublisherMap().put(MembersChangeEvent.class.getCanonicalName(),
            eventPublisher);
        // Pin serverList to exactly self + 1.1.1.1 so tests are deterministic (lookup may add members in some envs)
        ConcurrentSkipListMap<String, Member> pinned = new ConcurrentSkipListMap<>();
        pinned.put(serverMemberManager.getSelf().getAddress(), serverMemberManager.getSelf());
        pinned.put("1.1.1.1:8848", serverMemberManager.find("1.1.1.1:8848"));
        ReflectionTestUtils.setField(serverMemberManager, "serverList", pinned);
        serverMemberManager.getMemberAddressInfos().clear();
        serverMemberManager.getMemberAddressInfos().add(serverMemberManager.getSelf().getAddress());
        serverMemberManager.getMemberAddressInfos().add("1.1.1.1:8848");
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        EVENT_PUBLISH.set(false);
        NotifyCenter.deregisterPublisher(MembersChangeEvent.class);
        serverMemberManager.shutdown();
    }
    
    /**
     * Covers update() when address is not in serverList (lines 259-261: warn log and return false).
     */
    @Test
    void testUpdateNonExistMember() {
        Member newMember = Member.builder().ip("1.1.1.2").port(8848).state(NodeState.UP).build();
        assertFalse(serverMemberManager.update(newMember));
        assertFalse(serverMemberManager
            .update(Member.builder().ip("10.0.0.1").port(9999).state(NodeState.UP).build()));
    }
    
    @Test
    void testUpdateDownMember() {
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        assertTrue(serverMemberManager.update(newMember));
        assertFalse(serverMemberManager.getMemberAddressInfos().contains("1.1.1.1:8848"));
        verify(eventPublisher).publish(any(MembersChangeEvent.class));
    }
    
    @Test
    void testUpdateVersionMember() {
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build();
        newMember.setExtendVal(MemberMetaDataConstants.VERSION, "testVersion");
        assertTrue(serverMemberManager.update(newMember));
        assertTrue(serverMemberManager.getMemberAddressInfos().contains("1.1.1.1:8848"));
        assertEquals("testVersion",
            serverMemberManager.getServerList().get("1.1.1.1:8848")
                .getExtendVal(MemberMetaDataConstants.VERSION));
        verify(eventPublisher).publish(any(MembersChangeEvent.class));
    }
    
    @Test
    void testUpdateNonBasicExtendInfoMember() {
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build();
        newMember.setExtendVal("naming", "test");
        assertTrue(serverMemberManager.update(newMember));
        assertTrue(serverMemberManager.getMemberAddressInfos().contains("1.1.1.1:8848"));
        assertEquals("test",
            serverMemberManager.getServerList().get("1.1.1.1:8848").getExtendVal("naming"));
        verify(eventPublisher, never()).publish(any(MembersChangeEvent.class));
    }
    
    @Test
    void testHasMember() {
        assertTrue(serverMemberManager.hasMember("1.1.1.1"));
    }
    
    @Test
    void testHasMemberExactAddress() {
        assertTrue(serverMemberManager.hasMember("1.1.1.1:8848"));
    }
    
    @Test
    void testHasMemberShouldNotMatchIpPrefixOrSubstring() {
        // serverList contains the member "1.1.1.1:8848".
        assertTrue(serverMemberManager.hasMember("1.1.1.1"));
        assertTrue(serverMemberManager.hasMember("1.1.1.1:8848"));
        // None of the following are members; the previous substring match wrongly
        // returned true for all of them.
        assertFalse(serverMemberManager.hasMember("1.1.1"));
        assertFalse(serverMemberManager.hasMember("1.1.1.1:88"));
        assertFalse(serverMemberManager.hasMember("8848"));
    }
    
    @Test
    void testHasMemberIpv4PrefixCollision() {
        Member member =
            Member.builder().ip("192.168.1.100").port(8848).state(NodeState.UP).build();
        assertTrue(serverMemberManager.memberJoin(Collections.singletonList(member)));
        assertTrue(serverMemberManager.hasMember("192.168.1.100"));
        assertTrue(serverMemberManager.hasMember("192.168.1.100:8848"));
        // "192.168.1.10" is a prefix of the member IP but is not a member itself.
        assertFalse(serverMemberManager.hasMember("192.168.1.10"));
    }
    
    @Test
    void testHasMemberIpv6() {
        Member member = Member.builder().ip("[::1]").port(8848).state(NodeState.UP).build();
        assertTrue(serverMemberManager.memberJoin(Collections.singletonList(member)));
        // IPv6 lookups must work and must not be broken by naive ":" splitting.
        assertTrue(serverMemberManager.hasMember(member.getIp()));
        assertTrue(serverMemberManager.hasMember(member.getAddress()));
        assertFalse(serverMemberManager.hasMember("[::2]"));
    }
    
    @Test
    void testMemberLeave() {
        Member member = Member.builder().ip("1.1.3.3").port(8848).state(NodeState.DOWN).build();
        boolean joinResult = serverMemberManager.memberJoin(Collections.singletonList(member));
        assertTrue(joinResult);
        
        List<String> ips = serverMemberManager.getServerListUnhealth();
        assertEquals(1, ips.size());
        
        boolean result = serverMemberManager.memberLeave(Collections.singletonList(member));
        assertTrue(result);
    }
    
    @Test
    void testIsUnHealth() {
        assertFalse(serverMemberManager.isUnHealth("1.1.1.1"));
    }
    
    @Test
    void testIsUnHealthWhenMemberDown() {
        serverMemberManager
            .update(Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build());
        assertTrue(serverMemberManager.isUnHealth("1.1.1.1:8848"));
    }
    
    @Test
    void testIsUnHealthUnknownAddress() {
        assertFalse(serverMemberManager.isUnHealth("10.0.0.1:8848"));
    }
    
    @Test
    void testStateCheck() {
        assertTrue(serverMemberManager.stateCheck("1.1.1.1:8848",
            Collections.singletonList(NodeState.UP)));
        assertTrue(
            serverMemberManager.stateCheck("1.1.1.1:8848", List.of(NodeState.UP, NodeState.DOWN)));
        assertFalse(serverMemberManager.stateCheck("10.0.0.1:8848",
            Collections.singletonList(NodeState.UP)));
        serverMemberManager
            .update(Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build());
        assertTrue(serverMemberManager.stateCheck("1.1.1.1:8848",
            Collections.singletonList(NodeState.DOWN)));
    }
    
    @Test
    void testIsFirstIp() {
        assertFalse(serverMemberManager.isFirstIp());
    }
    
    @Test
    void testGetServerList() {
        assertEquals(2, serverMemberManager.getServerList().size());
        Map<String, Member> list = serverMemberManager.getServerList();
        assertThrows(UnsupportedOperationException.class,
            () -> list.put("x", Member.builder().ip("x").port(1).build()));
    }
    
    /**
     * After init(), serverList always contains at least self (line 168). Nothing between 168 and 177 removes it,
     * so the throw at line 177-178 (serverList.isEmpty()) is theoretically unreachable in normal flow.
     */
    @Test
    void testInitServerListNeverEmpty() {
        assertFalse(serverMemberManager.getServerList().isEmpty());
        assertTrue(serverMemberManager.getServerList()
            .containsKey(serverMemberManager.getSelf().getAddress()));
    }
    
    @Test
    void testSetSelfReady() {
        serverMemberManager.setSelfReady(8848);
        assertEquals(NodeState.UP, serverMemberManager.getSelf().getState());
    }
    
    @Test
    void testUnhealthyMemberInfoReportTaskRunsAfter() {
        Member downMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        downMember.setAbilities(new ServerAbilities());
        downMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(downMember);
        Object unhealthyTask =
            ReflectionTestUtils.getField(serverMemberManager, "unhealthyMemberInfoReportTask");
        ((Runnable) unhealthyTask).run();
    }
    
    /**
     * Covers memberChange() when self is not in the incoming members list (lines 367-370: isInIpList = false, add self, warn log).
     * Must pass a mutable collection because memberChange() adds self when not in list.
     */
    @Test
    void testMemberChangeWhenSelfNotInList() {
        Member other = Member.builder().ip("2.2.2.2").port(8848).state(NodeState.UP).build();
        boolean result = serverMemberManager
            .memberChange(new java.util.ArrayList<>(Collections.singletonList(other)));
        assertTrue(result);
        assertTrue(serverMemberManager.getServerList()
            .containsKey(serverMemberManager.getSelf().getAddress()));
        assertTrue(serverMemberManager.getServerList().containsKey("2.2.2.2:8848"));
    }
    
    /**
     * Covers memberChange() when there is no change (lines 411-414: hasChange false, debug log branch).
     */
    @Test
    void testMemberChangeWhenNoChange() {
        Collection<Member> current = serverMemberManager.allMembers();
        boolean result = serverMemberManager.memberChange(new java.util.ArrayList<>(current));
        assertFalse(result);
        assertEquals(current.size(), serverMemberManager.getServerList().size());
    }
    
    /**
     * Covers stateCheck(address, ...) and isUnHealth(address) when member is null (address not in serverList) — lines 451-453 and 471-472.
     */
    @Test
    void testStateCheckAndIsUnHealthWhenAddressNotInList() {
        assertFalse(serverMemberManager.stateCheck("0.0.0.0:9999",
            Collections.singletonList(NodeState.UP)));
        assertFalse(serverMemberManager.isUnHealth("0.0.0.0:9999"));
    }
    
    @Test
    void testHttpReportTaskWithoutMemberInfo() throws NacosException {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.VERSION, "test")).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        assertTrue(
            serverMemberManager.find("1.1.1.1:8848").getExtendInfo()
                .containsKey(MemberMetaDataConstants.VERSION));
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            RestResult<String> result = RestResultUtils.success("true");
            callback.onReceive(result);
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertEquals("test",
            serverMemberManager.find("1.1.1.1:8848").getExtendVal(MemberMetaDataConstants.VERSION));
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testGrpcReportTaskWithoutMemberInfo() throws NacosException {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.VERSION, "test")).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        serverMemberManager.updateMember(testMember);
        assertTrue(
            serverMemberManager.find("1.1.1.1:8848").getExtendInfo()
                .containsKey(MemberMetaDataConstants.VERSION));
        ServerMemberManager.MemberInfoReportTask infoReportTask =
            serverMemberManager.getInfoReportTask();
        ClusterRpcClientProxy clusterRpcClientProxy = mock(ClusterRpcClientProxy.class);
        ReflectionTestUtils.setField(infoReportTask, "clusterRpcClientProxy",
            clusterRpcClientProxy);
        when(clusterRpcClientProxy.isRunning(any())).thenReturn(true);
        testMember.setState(NodeState.UP);
        when(clusterRpcClientProxy.sendRequest(any(), any()))
            .thenReturn(new MemberReportResponse(testMember));
        infoReportTask.run();
        assertEquals("test",
            serverMemberManager.find("1.1.1.1:8848").getExtendVal(MemberMetaDataConstants.VERSION));
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testHttpReportTaskHandleReportResultInvalidJson() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            RestResult<String> result = RestResultUtils.success("not-valid-json");
            callback.onReceive(result);
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testHttpReportTaskHandleReportResultBooleanTrue() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            RestResult<String> result = RestResultUtils.success(Boolean.TRUE.toString());
            callback.onReceive(result);
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testHttpReportTaskWithMemberInfoChanged() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.VERSION, "test")).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        assertTrue(
            serverMemberManager.find("1.1.1.1:8848").getExtendInfo()
                .containsKey(MemberMetaDataConstants.VERSION));
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.VERSION, "new")).build();
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            RestResult<String> result = RestResultUtils.success(JacksonUtils.toJson(newMember));
            callback.onReceive(result);
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertEquals("new",
            serverMemberManager.find("1.1.1.1:8848").getExtendVal(MemberMetaDataConstants.VERSION));
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testGrpcReportTaskWithMemberInfoChanged() throws NacosException {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.VERSION, "test")).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        serverMemberManager.updateMember(testMember);
        assertTrue(
            serverMemberManager.find("1.1.1.1:8848").getExtendInfo()
                .containsKey(MemberMetaDataConstants.VERSION));
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.VERSION, "new")).build();
        ServerMemberManager.MemberInfoReportTask infoReportTask =
            serverMemberManager.getInfoReportTask();
        ClusterRpcClientProxy clusterRpcClientProxy = mock(ClusterRpcClientProxy.class);
        ReflectionTestUtils.setField(infoReportTask, "clusterRpcClientProxy",
            clusterRpcClientProxy);
        when(clusterRpcClientProxy.isRunning(any())).thenReturn(true);
        when(clusterRpcClientProxy.sendRequest(any(), any()))
            .thenReturn(new MemberReportResponse(newMember));
        infoReportTask.run();
        assertEquals("new",
            serverMemberManager.find("1.1.1.1:8848").getExtendVal(MemberMetaDataConstants.VERSION));
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testUnhealthyMemberInfoReportTaskRun() throws NacosException {
        Member downMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        downMember.setAbilities(new ServerAbilities());
        downMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        downMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(true);
        serverMemberManager.updateMember(downMember);
        
        Object unhealthyTask =
            ReflectionTestUtils.getField(serverMemberManager, "unhealthyMemberInfoReportTask");
        assertTrue(unhealthyTask instanceof Runnable);
        ClusterRpcClientProxy clusterRpcClientProxy = mock(ClusterRpcClientProxy.class);
        ReflectionTestUtils.setField(unhealthyTask, "clusterRpcClientProxy", clusterRpcClientProxy);
        when(clusterRpcClientProxy.isRunning(any())).thenReturn(true);
        Member upMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build();
        when(clusterRpcClientProxy.sendRequest(any(), any()))
            .thenReturn(new MemberReportResponse(upMember));
        
        ((Runnable) unhealthyTask).run();
        assertEquals(NodeState.UP, serverMemberManager.find("1.1.1.1:8848").getState());
    }
    
    @Test
    void testMemberInfoReportTaskRunWhenMembersEmpty() {
        ConcurrentSkipListMap<String, Member> onlySelf = new ConcurrentSkipListMap<>();
        onlySelf.put(serverMemberManager.getSelf().getAddress(), serverMemberManager.getSelf());
        ReflectionTestUtils.setField(serverMemberManager, "serverList", onlySelf);
        serverMemberManager.getMemberAddressInfos().clear();
        serverMemberManager.getMemberAddressInfos().add(serverMemberManager.getSelf().getAddress());
        serverMemberManager.getInfoReportTask().run();
    }
    
    @Test
    void testHttpReportTaskCallbackOnReceiveNotOk() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            RestResult<String> result = RestResultUtils.failed("error");
            callback.onReceive(result);
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertTrue(serverMemberManager.find("1.1.1.1:8848").isGrpcReportEnabled());
    }
    
    @Test
    void testHttpReportTaskCallbackOnError() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            callback.onError(new RuntimeException("mock error"));
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertTrue(serverMemberManager.find("1.1.1.1:8848").isGrpcReportEnabled());
    }
    
    @Test
    void testHttpReportTaskCallbackOnCancel() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocationOnMock -> {
            Callback<String> callback = invocationOnMock.getArgument(5);
            callback.onCancel();
            return null;
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
    }
    
    @Test
    void testHttpReportTaskPostThrows() {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(false);
        serverMemberManager.updateMember(testMember);
        NacosAsyncRestTemplate mockAsyncRestTemplate = mock(NacosAsyncRestTemplate.class);
        ReflectionTestUtils.setField(serverMemberManager, "asyncRestTemplate",
            mockAsyncRestTemplate);
        doAnswer(invocation -> {
            throw new RuntimeException("post failed");
        }).when(mockAsyncRestTemplate).post(anyString(), any(), any(), any(), any(), any());
        serverMemberManager.getInfoReportTask().run();
        assertTrue(serverMemberManager.find("1.1.1.1:8848").isGrpcReportEnabled());
    }
    
    @Test
    void testGrpcReportTaskWhenNotRunning() throws NacosException {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        serverMemberManager.updateMember(testMember);
        ServerMemberManager.MemberInfoReportTask infoReportTask =
            serverMemberManager.getInfoReportTask();
        ClusterRpcClientProxy clusterRpcClientProxy = mock(ClusterRpcClientProxy.class);
        ReflectionTestUtils.setField(infoReportTask, "clusterRpcClientProxy",
            clusterRpcClientProxy);
        when(clusterRpcClientProxy.isRunning(any())).thenReturn(false);
        infoReportTask.run();
    }
    
    @Test
    void testGrpcReportTaskResponseNotSuccess() throws NacosException {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        serverMemberManager.updateMember(testMember);
        ServerMemberManager.MemberInfoReportTask infoReportTask =
            serverMemberManager.getInfoReportTask();
        ClusterRpcClientProxy clusterRpcClientProxy = mock(ClusterRpcClientProxy.class);
        ReflectionTestUtils.setField(infoReportTask, "clusterRpcClientProxy",
            clusterRpcClientProxy);
        when(clusterRpcClientProxy.isRunning(any())).thenReturn(true);
        MemberReportResponse errorResponse = new MemberReportResponse();
        errorResponse.setErrorInfo(500, "server error");
        when(clusterRpcClientProxy.sendRequest(any(), any())).thenReturn(errorResponse);
        infoReportTask.run();
    }
    
    @Test
    void testGrpcReportTaskNoHandlerException() throws NacosException {
        Member testMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        testMember.setAbilities(new ServerAbilities());
        testMember.getAbilities().getRemoteAbility().setSupportRemoteConnection(true);
        testMember.getAbilities().getRemoteAbility().setGrpcReportEnabled(true);
        serverMemberManager.updateMember(testMember);
        ServerMemberManager.MemberInfoReportTask infoReportTask =
            serverMemberManager.getInfoReportTask();
        ClusterRpcClientProxy clusterRpcClientProxy = mock(ClusterRpcClientProxy.class);
        ReflectionTestUtils.setField(infoReportTask, "clusterRpcClientProxy",
            clusterRpcClientProxy);
        when(clusterRpcClientProxy.isRunning(any())).thenReturn(true);
        when(clusterRpcClientProxy.sendRequest(any(), any()))
            .thenThrow(new NacosException(NacosException.NO_HANDLER, "no handler"));
        infoReportTask.run();
        assertFalse(serverMemberManager.find("1.1.1.1:8848").getAbilities().getRemoteAbility()
            .isGrpcReportEnabled());
        assertFalse(serverMemberManager.find("1.1.1.1:8848").isGrpcReportEnabled());
    }
    
    @Test
    void testIpChangeEventSubscriber() throws InterruptedException {
        // Use fixed IPs so test is deterministic across environments (e.g. cloud runners use hostnames).
        String oldIp = "192.168.1.1";
        String newIp = "192.168.2.100";
        int port = 8848;
        Member self = serverMemberManager.getSelf();
        self.setIp(oldIp);
        String oldAddress = oldIp + ":" + port;
        ReflectionTestUtils.setField(serverMemberManager, "localAddress", oldAddress);
        @SuppressWarnings("unchecked")
        ConcurrentSkipListMap<String, Member> serverList =
            (ConcurrentSkipListMap<String, Member>) ReflectionTestUtils
                .getField(serverMemberManager, "serverList");
        String previousAddress = serverList.firstKey();
        serverList.remove(previousAddress);
        serverList.put(oldAddress, self);
        serverMemberManager.getMemberAddressInfos().remove(previousAddress);
        serverMemberManager.getMemberAddressInfos().add(oldAddress);
        
        InetUtils.IPChangeEvent event = new InetUtils.IPChangeEvent();
        event.setOldIP(oldIp);
        event.setNewIP(newIp);
        NotifyCenter.publishEvent(event);
        for (int i = 0; i < 100; i++) {
            if (newIp.equals(serverMemberManager.getSelf().getIp())) {
                break;
            }
            Thread.sleep(100);
        }
        assertEquals(newIp, serverMemberManager.getSelf().getIp(),
            "IP change event should be processed; async subscriber may be slow in some environments");
        String newAddress = newIp + ":" + port;
        assertTrue(serverMemberManager.getServerList().containsKey(newAddress));
        assertTrue(serverMemberManager.getMemberAddressInfos().contains(newAddress));
        InetUtils.IPChangeEvent restoreEvent = new InetUtils.IPChangeEvent();
        restoreEvent.setOldIP(newIp);
        restoreEvent.setNewIP(oldIp);
        NotifyCenter.publishEvent(restoreEvent);
        for (int i = 0; i < 100; i++) {
            if (oldIp.equals(serverMemberManager.getSelf().getIp())) {
                break;
            }
            Thread.sleep(100);
        }
    }
}
