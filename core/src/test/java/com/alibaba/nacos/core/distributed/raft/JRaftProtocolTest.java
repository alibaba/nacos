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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.raft.exception.NoSuchRaftGroupException;
import com.alipay.sofa.jraft.Node;
import com.google.protobuf.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class JRaftProtocolTest {
    
    @Mock
    private JRaftServer serverMock;
    
    private JRaftProtocol raftProtocol;
    
    @Mock
    private JRaftMaintainService jRaftMaintainService;
    
    @Mock
    private ServerMemberManager memberManager;
    
    private ReadRequest readRequest;
    
    private WriteRequest writeRequest;
    
    @Mock
    private CompletableFuture<Response> futureMock;
    
    @Mock
    private Node nodeMock;
    
    private String groupId;
    
    @BeforeEach
    void setUp() throws Exception {
        raftProtocol = new JRaftProtocol(memberManager);
        ReadRequest.Builder readRequestBuilder = ReadRequest.newBuilder();
        readRequest = readRequestBuilder.build();
        
        WriteRequest.Builder writeRequestBuilder = WriteRequest.newBuilder();
        writeRequest = writeRequestBuilder.build();
        
        Field raftServerField = JRaftProtocol.class.getDeclaredField("raftServer");
        raftServerField.setAccessible(true);
        raftServerField.set(raftProtocol, serverMock);
        
        Field jRaftMaintainServiceField =
            JRaftProtocol.class.getDeclaredField("jRaftMaintainService");
        jRaftMaintainServiceField.setAccessible(true);
        jRaftMaintainServiceField.set(raftProtocol, jRaftMaintainService);
        
        when(serverMock.get(readRequest)).thenReturn(futureMock);
        when(serverMock.commit(any(String.class), any(Message.class), any(CompletableFuture.class)))
            .thenReturn(
                futureMock);
        
        groupId = "test_group";
        when(serverMock.findNodeByGroup(groupId)).thenReturn(nodeMock);
    }
    
    @Test
    void testGetData() throws Exception {
        raftProtocol.getData(readRequest);
        verify(serverMock).get(readRequest);
    }
    
    @Test
    void testWrite() throws Exception {
        raftProtocol.write(writeRequest);
        verify(serverMock).commit(any(String.class), eq(writeRequest),
            any(CompletableFuture.class));
    }
    
    @Test
    void testMemberChange() {
        Set<String> addresses = new HashSet<>();
        raftProtocol.memberChange(addresses);
        verify(serverMock, times(5)).peerChange(jRaftMaintainService, addresses);
    }
    
    @Test
    void testIsLeader() {
        raftProtocol.isLeader(groupId);
        verify(serverMock).findNodeByGroup(groupId);
        verify(nodeMock).isLeader();
    }
    
    @Test
    void testIsReady() {
        raftProtocol.isReady();
        verify(serverMock).isReady();
    }
    
    @Test
    void testIsLeaderWhenNodeNotFoundThrowsNoSuchRaftGroupException() {
        when(serverMock.findNodeByGroup("missing-group")).thenReturn(null);
        assertThrows(NoSuchRaftGroupException.class, () -> raftProtocol.isLeader("missing-group"));
        verify(serverMock).findNodeByGroup("missing-group");
    }
    
    @Test
    void testExecuteDelegatesToMaintainService() {
        when(jRaftMaintainService.execute(anyMap())).thenReturn(RestResultUtils.success("ok"));
        RestResult<String> result =
            raftProtocol.execute(Collections.singletonMap("command", "doSnapshot"));
        verify(jRaftMaintainService).execute(anyMap());
    }
    
    @Test
    void testAddRequestProcessors() {
        raftProtocol.addRequestProcessors(Collections.emptyList());
        verify(serverMock).createMultiRaftGroup(Collections.emptyList());
    }
    
    @Test
    void testShutdownWhenInitializedCallsRaftServerShutdown() throws Exception {
        Field initializedField = JRaftProtocol.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        ((AtomicBoolean) initializedField.get(raftProtocol)).set(true);
        raftProtocol.shutdown();
        verify(serverMock).shutdown();
    }
    
    @Test
    void testShutdownWhenNotInitializedDoesNotCallRaftServerShutdown() throws Exception {
        Field initializedField = JRaftProtocol.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        ((AtomicBoolean) initializedField.get(raftProtocol)).set(false);
        raftProtocol.shutdown();
        verify(serverMock, never()).shutdown();
    }
    
    @Test
    void testAGetDataDelegatesToRaftServer() {
        raftProtocol.aGetData(readRequest);
        verify(serverMock).get(readRequest);
    }
    
    @Test
    void testWriteAsyncDelegatesToRaftServer() {
        raftProtocol.writeAsync(writeRequest);
        verify(serverMock).commit(eq(writeRequest.getGroup()), eq(writeRequest),
            any(CompletableFuture.class));
    }
    
    /**
     * When peerChange returns true on first call, memberChange returns without retrying 5 times.
     */
    @Test
    void testMemberChangeSucceedsOnFirstTry() {
        Set<String> addresses = new HashSet<>();
        when(serverMock.peerChange(any(JRaftMaintainService.class), eq(addresses)))
            .thenReturn(true);
        raftProtocol.memberChange(addresses);
        verify(serverMock, times(1)).peerChange(any(JRaftMaintainService.class), eq(addresses));
    }
    
    /**
     * Second shutdown does not call raftServer.shutdown again (idempotent).
     */
    @Test
    void testShutdownWhenAlreadyShutdownedDoesNotCallRaftServerShutdownAgain() throws Exception {
        Field initializedField = JRaftProtocol.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        Field shutdownedField = JRaftProtocol.class.getDeclaredField("shutdowned");
        shutdownedField.setAccessible(true);
        ((AtomicBoolean) initializedField.get(raftProtocol)).set(true);
        raftProtocol.shutdown();
        raftProtocol.shutdown();
        verify(serverMock, times(1)).shutdown();
    }
    
    /**
     * When already initialized, second init() does not call raftServer.init/start again.
     */
    @Test
    void testInitWhenAlreadyInitializedDoesNotReinit() throws Exception {
        RaftConfig config = new RaftConfig();
        config.setMembers("127.0.0.1:7848", Collections.singleton("127.0.0.1:7848"));
        raftProtocol.init(config);
        verify(serverMock, times(1)).init(any(RaftConfig.class));
        verify(serverMock, times(1)).start();
        raftProtocol.init(config);
        verify(serverMock, times(1)).init(any(RaftConfig.class));
        verify(serverMock, times(1)).start();
    }
}
