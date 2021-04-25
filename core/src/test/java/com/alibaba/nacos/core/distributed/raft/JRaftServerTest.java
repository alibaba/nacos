/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosure;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.core.NodeImpl;
import com.alipay.sofa.jraft.core.State;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.rpc.InvokeCallback;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.impl.cli.CliClientServiceImpl;
import com.alipay.sofa.jraft.util.Endpoint;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.impl.FutureImpl;
import com.google.protobuf.Message;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JRaftServerTest {
    
    private PeerId peerId1;
    
    private PeerId peerId2;
    
    private PeerId peerId3;
    
    private Configuration conf;
    
    private final String groupId = "test_group";
    
    private JRaftServer server;
    
    @Mock
    private RequestProcessor4CP mockProcessor4CP;
    
    @BeforeClass
    public static void beforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Mock
    private CliClientServiceImpl cliClientServiceMock;
    
    @Mock
    private CliService cliServiceMock;
    
    @Mock
    private RpcClient rpcClient;
    
    @Mock
    private CompletableFuture<Response> future;
    
    private ReadRequest readRequest;
    
    @Before
    public void before() throws NoSuchFieldException, IllegalAccessException {
        initPeersAndConfiguration();
        RaftConfig config = new RaftConfig();
        Collection<Member> initEvent = Collections.singletonList(Member.builder().ip("1.1.1.1").port(7848).build());
        config.setMembers("1.1.1.1:7848", ProtocolManager.toCPMembersInfo(initEvent));
        
        server = new JRaftServer() {
            
            @Override
            boolean peerChange(JRaftMaintainService maintainService, Set<String> newPeers) {
                return super.peerChange(maintainService, newPeers);
            }
        };
        
        server.init(config);
        
        Map<String, JRaftServer.RaftGroupTuple> map = new HashMap<>();
        map.put("test_nacos", new JRaftServer.RaftGroupTuple());
        server.mockMultiRaftGroup(map);
        
        mockcliClientService();
        mockcliService();
        setLeaderAs(peerId1);
        // Inject the mocked cliClientServiceMock into server.
        Field cliClientServiceField = JRaftServer.class.getDeclaredField("cliClientService");
        cliClientServiceField.setAccessible(true);
        cliClientServiceField.set(server, cliClientServiceMock);
    
        // Inject the mocked cliServiceMock into server.
        Field cliServiceField = JRaftServer.class.getDeclaredField("cliService");
        cliServiceField.setAccessible(true);
        cliServiceField.set(server, cliServiceMock);
        
        // currently useless
        ReadRequest.Builder readRequestBuilder = ReadRequest.newBuilder();
        readRequest = readRequestBuilder.build();
        
        when(mockProcessor4CP.loadSnapshotOperate()).thenReturn(Collections.emptyList());
        when(mockProcessor4CP.group()).thenReturn(groupId);
        
        when(future.completeExceptionally(any(IllegalArgumentException.class))).thenReturn(true);
        
        Field isStartedField = JRaftServer.class.getDeclaredField("isStarted");
        isStartedField.setAccessible(true);
        isStartedField.set(server, true);
    }
    
    private void initPeersAndConfiguration() {
        peerId1 = new PeerId("11.11.11.11", 7848);
        peerId2 = new PeerId("22.22.22.22", 7848);
        peerId3 = new PeerId("33.33.33.33", 7848);
        this.conf = new Configuration();
        conf.addPeer(peerId1);
        conf.addPeer(peerId2);
        conf.addPeer(peerId3);
        RouteTable.getInstance().updateConfiguration(groupId, conf);
    }
    
    private void mockcliClientService() {
        when(cliClientServiceMock.connect(any(Endpoint.class))).thenReturn(true);
        // Assign PeerId1 as the leader
        final CliRequests.GetLeaderRequest.Builder rb = CliRequests.GetLeaderRequest.newBuilder();
        rb.setGroupId(groupId);
        final CliRequests.GetLeaderRequest getLeaderRequest = rb.build();
        final FutureImpl<Message> getLeaderFuture = new FutureImpl<>();
        final CliRequests.GetLeaderResponse.Builder gb = CliRequests.GetLeaderResponse.newBuilder();
        gb.setLeaderId(peerId1.toString());
        final CliRequests.GetLeaderResponse getLeaderResponse = gb.build();
        getLeaderFuture.setResult(getLeaderResponse);
        when(cliClientServiceMock.getLeader(peerId1.getEndpoint(), getLeaderRequest, null))
                .thenReturn(getLeaderFuture);
    }
    
    private void mockcliService() {
        List<PeerId> peerIds = new ArrayList<>();
        peerIds.add(peerId1);
        peerIds.add(peerId2);
        peerIds.add(peerId3);
        when(cliServiceMock.getPeers(groupId, conf)).thenReturn(peerIds);
        when(cliServiceMock.addPeer(eq(groupId), eq(conf), any(PeerId.class))).thenReturn(Status.OK());
    }
    
    private void setLeaderAs(PeerId peerId) {
        RouteTable.getInstance().updateLeader(groupId, peerId);
    }
    
    @Test
    public void testInvokeToLeader()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, RemotingException, InterruptedException {
        when(cliClientServiceMock.getRpcClient()).thenReturn(rpcClient);
        setLeaderAs(peerId1);
        int timeout = 3000;
        Method invokeToLeaderMethod = JRaftServer.class.getDeclaredMethod("invokeToLeader",
                String.class, Message.class, int.class, FailoverClosure.class);
        invokeToLeaderMethod.setAccessible(true);
        invokeToLeaderMethod.invoke(server, groupId, this.readRequest, timeout, null);
        verify(cliClientServiceMock).getRpcClient();
        verify(rpcClient).invokeAsync(eq(peerId1.getEndpoint()), eq(readRequest), any(InvokeCallback.class), any(long.class));
    }
    
    @Test
    public void testRefreshRouteTable() {
        server.refreshRouteTable(groupId);
        verify(cliClientServiceMock, times(2)).connect(peerId1.getEndpoint());
        verify(cliClientServiceMock).getLeader(eq(peerId1.getEndpoint()), any(CliRequests.GetLeaderRequest.class), eq(null));
    }
    
    @Test
    public void testCommit() throws NoSuchFieldException, IllegalAccessException, TimeoutException, InterruptedException {
        WriteRequest.Builder writeRequestBuilder = WriteRequest.newBuilder();
        WriteRequest writeRequest = writeRequestBuilder.build();
    
        // No group is set, and make sure that an IllegalArgumentException will be thrown.
        CompletableFuture<Response> future = server.commit(groupId, writeRequest, this.future);
        verify(future).completeExceptionally(any(IllegalArgumentException.class));
        
        // Set an group.
        Collection<RequestProcessor4CP> processors = Collections.singletonList(mockProcessor4CP);
        server.createMultiRaftGroup(processors);
        
        Field cliClientServiceField = JRaftServer.class.getDeclaredField("cliClientService");
        cliClientServiceField.setAccessible(true);
        cliClientServiceField.set(server, cliClientServiceMock);
        
        // Make the node leader and verify the invokeToLeader is never called.
        NodeImpl node = (NodeImpl) server.findNodeByGroup(groupId);
        Field stateField = NodeImpl.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(node, State.STATE_LEADER);
    
        server.commit(groupId, writeRequest, future);
        verify(cliClientServiceMock, never()).getRpcClient();
    
        // make the node follower and verify the invokeToLeader is called.
        node = (NodeImpl) server.findNodeByGroup(groupId);
        stateField.setAccessible(true);
        stateField.set(node, State.STATE_FOLLOWER);
        
        RouteTable.getInstance().updateLeader(groupId, peerId1);
        server.commit(groupId, writeRequest, future);
        verify(cliClientServiceMock).getRpcClient();
    }
    
    @Test
    public void testRegisterSelfToCluster() {
        PeerId selfPeerId = new PeerId("4.4.4.4", 8080);
        server.registerSelfToCluster(groupId, selfPeerId, conf);
        verify(cliServiceMock).addPeer(groupId, conf, selfPeerId);
    }
    
    @Test
    public void testPeerChange() {
        AtomicBoolean changed = new AtomicBoolean(false);
        
        JRaftMaintainService service = new JRaftMaintainService(server) {
            @Override
            public RestResult<String> execute(Map<String, String> args) {
                changed.set(true);
                return RestResultUtils.success();
            }
        };
        
        Collection<Member> firstEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build(), Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.3").port(82).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(firstEvent));
        Assert.assertFalse(changed.get());
        changed.set(false);
        
        Collection<Member> secondEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build(), Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.4").port(83).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(secondEvent));
        Assert.assertTrue(changed.get());
        changed.set(false);
        
        Collection<Member> thirdEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.5").port(82).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(thirdEvent));
        Assert.assertTrue(changed.get());
        changed.set(false);
        
        Collection<Member> fourEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(fourEvent));
        Assert.assertTrue(changed.get());
        changed.set(false);
        
        Collection<Member> fiveEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build(), Member.builder().ip("127.0.0.3").port(81).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(fiveEvent));
        Assert.assertFalse(changed.get());
        changed.set(false);
    }
    
    @After
    public void shutdown() {
        server.shutdown();
    }
    
}
