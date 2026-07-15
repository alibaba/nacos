/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.common.model.RestResult;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JRaftOps} execute() methods.
 */
@ExtendWith(MockitoExtension.class)
class JRaftOpsExecuteTest {
    
    private static final String GROUP_ID = "naming_persistent_service";
    
    @Mock
    private CliService cliService;
    
    @Mock
    private Node node;
    
    @Mock
    private NodeId nodeId;
    
    private Configuration conf;
    private NodeOptions nodeOptions;
    private Map<String, String> args;
    
    @BeforeEach
    void setUp() throws Exception {
        conf = new Configuration();
        conf.addPeer(PeerId.parsePeer("127.0.0.1:8080"));
        nodeOptions = new NodeOptions();
        nodeOptions.setInitialConf(conf);
        lenient().when(node.getOptions()).thenReturn(nodeOptions);
        args = new HashMap<>();
    }
    
    @Test
    void testTransferLeaderExecuteSuccess() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081");
        when(cliService.transferLeader(eq(GROUP_ID), eq(conf), any(PeerId.class)))
            .thenReturn(Status.OK());
        
        RestResult<String> result =
            JRaftOps.TRANSFER_LEADER.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testTransferLeaderExecuteFailure() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081");
        when(cliService.transferLeader(eq(GROUP_ID), eq(conf), any(PeerId.class)))
            .thenReturn(new Status(-1, "transfer failed"));
        
        RestResult<String> result =
            JRaftOps.TRANSFER_LEADER.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("transfer failed", result.getMessage());
    }
    
    @Test
    void testResetRaftClusterExecuteSuccess() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080,127.0.0.1:8081");
        when(cliService.changePeers(eq(GROUP_ID), eq(conf), any(Configuration.class)))
            .thenReturn(Status.OK());
        
        RestResult<String> result =
            JRaftOps.RESET_RAFT_CLUSTER.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testResetRaftClusterExecuteFailure() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080");
        when(cliService.changePeers(eq(GROUP_ID), eq(conf), any(Configuration.class)))
            .thenReturn(new Status(-1, "change peers failed"));
        
        RestResult<String> result =
            JRaftOps.RESET_RAFT_CLUSTER.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("change peers failed", result.getMessage());
    }
    
    @Test
    void testDoSnapshotExecuteSuccess() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080");
        when(cliService.snapshot(eq(GROUP_ID), any(PeerId.class))).thenReturn(Status.OK());
        
        RestResult<String> result = JRaftOps.DO_SNAPSHOT.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testDoSnapshotExecuteFailure() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080");
        when(cliService.snapshot(eq(GROUP_ID), any(PeerId.class)))
            .thenReturn(new Status(-1, "snapshot failed"));
        
        RestResult<String> result = JRaftOps.DO_SNAPSHOT.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("snapshot failed", result.getMessage());
    }
    
    @Test
    void testRemovePeerExecuteSuccessWhenPeerExists() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081");
        PeerId toRemove = PeerId.parsePeer("127.0.0.1:8081");
        when(cliService.getPeers(eq(GROUP_ID), eq(conf))).thenReturn(java.util.Arrays.asList(
            PeerId.parsePeer("127.0.0.1:8080"), toRemove));
        when(cliService.removePeer(eq(GROUP_ID), eq(conf), eq(toRemove))).thenReturn(Status.OK());
        
        RestResult<String> result = JRaftOps.REMOVE_PEER.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testRemovePeerExecuteSuccessWhenPeerNotInConf() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8082");
        when(cliService.getPeers(eq(GROUP_ID), eq(conf)))
            .thenReturn(java.util.Collections.singletonList(
                PeerId.parsePeer("127.0.0.1:8080")));
        
        RestResult<String> result = JRaftOps.REMOVE_PEER.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testRemovePeerExecuteFailure() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081");
        PeerId toRemove = PeerId.parsePeer("127.0.0.1:8081");
        when(cliService.getPeers(eq(GROUP_ID), eq(conf))).thenReturn(java.util.Arrays.asList(
            PeerId.parsePeer("127.0.0.1:8080"), toRemove));
        when(cliService.removePeer(eq(GROUP_ID), eq(conf), eq(toRemove)))
            .thenReturn(new Status(-1, "remove peer failed"));
        
        RestResult<String> result = JRaftOps.REMOVE_PEER.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("remove peer failed", result.getMessage());
    }
    
    @Test
    void testRemovePeersExecuteSuccess() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081,127.0.0.1:8082");
        PeerId p1 = PeerId.parsePeer("127.0.0.1:8081");
        PeerId p2 = PeerId.parsePeer("127.0.0.1:8082");
        when(cliService.getPeers(eq(GROUP_ID), eq(conf)))
            .thenReturn(java.util.Arrays.asList(PeerId.parsePeer("127.0.0.1:8080"), p1, p2));
        when(cliService.removePeer(eq(GROUP_ID), eq(conf), eq(p1))).thenReturn(Status.OK());
        when(cliService.removePeer(eq(GROUP_ID), eq(conf), eq(p2))).thenReturn(Status.OK());
        
        RestResult<String> result = JRaftOps.REMOVE_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testRemovePeersExecuteFailureOnFirstRemove() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081");
        PeerId p1 = PeerId.parsePeer("127.0.0.1:8081");
        when(cliService.getPeers(eq(GROUP_ID), eq(conf)))
            .thenReturn(java.util.Arrays.asList(PeerId.parsePeer("127.0.0.1:8080"), p1));
        when(cliService.removePeer(eq(GROUP_ID), eq(conf), eq(p1)))
            .thenReturn(new Status(-1, "remove failed"));
        
        RestResult<String> result = JRaftOps.REMOVE_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("remove failed", result.getMessage());
    }
    
    @Test
    void testRemovePeersSkipsPeerNotInConf() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8081,127.0.0.1:8082");
        when(cliService.getPeers(eq(GROUP_ID), eq(conf)))
            .thenReturn(java.util.Collections.singletonList(
                PeerId.parsePeer("127.0.0.1:8080")));
        
        RestResult<String> result = JRaftOps.REMOVE_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testChangePeersExecuteSuccess() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080,127.0.0.1:8081");
        when(cliService.changePeers(eq(GROUP_ID), eq(conf), any(Configuration.class)))
            .thenReturn(Status.OK());
        
        RestResult<String> result = JRaftOps.CHANGE_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testChangePeersExecuteSuccessWhenConfUnchanged() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080");
        
        RestResult<String> result = JRaftOps.CHANGE_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testChangePeersExecuteFailure() {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        when(cliService.changePeers(eq(GROUP_ID), eq(conf), any(Configuration.class)))
            .thenReturn(new Status(-1, "change peers failed"));
        
        RestResult<String> result = JRaftOps.CHANGE_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("change peers failed", result.getMessage());
    }
    
    @Test
    void testResetPeersExecuteSuccess() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080,127.0.0.1:8081");
        PeerId selfPeer = PeerId.parsePeer("127.0.0.1:8080");
        when(node.getNodeId()).thenReturn(nodeId);
        when(nodeId.getPeerId()).thenReturn(selfPeer);
        when(cliService.resetPeer(eq(GROUP_ID), any(PeerId.class), any(Configuration.class)))
            .thenReturn(Status.OK());
        
        RestResult<String> result = JRaftOps.RESET_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertTrue(result.ok());
    }
    
    @Test
    void testResetPeersExecuteFailure() throws Exception {
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:8080");
        PeerId selfPeer = PeerId.parsePeer("127.0.0.1:8080");
        when(node.getNodeId()).thenReturn(nodeId);
        when(nodeId.getPeerId()).thenReturn(selfPeer);
        when(cliService.resetPeer(eq(GROUP_ID), any(PeerId.class), any(Configuration.class)))
            .thenReturn(new Status(-1, "reset peer failed"));
        
        RestResult<String> result = JRaftOps.RESET_PEERS.execute(cliService, GROUP_ID, node, args);
        
        assertFalse(result.ok());
        assertEquals("reset peer failed", result.getMessage());
    }
}
