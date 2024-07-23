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

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        
        Field jRaftMaintainServiceField = JRaftProtocol.class.getDeclaredField("jRaftMaintainService");
        jRaftMaintainServiceField.setAccessible(true);
        jRaftMaintainServiceField.set(raftProtocol, jRaftMaintainService);
        
        when(serverMock.get(readRequest)).thenReturn(futureMock);
        when(serverMock.commit(any(String.class), any(Message.class), any(CompletableFuture.class))).thenReturn(futureMock);
        
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
        verify(serverMock).commit(any(String.class), eq(writeRequest), any(CompletableFuture.class));
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
}