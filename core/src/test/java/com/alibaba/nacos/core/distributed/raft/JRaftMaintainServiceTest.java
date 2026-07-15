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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JRaftMaintainServiceTest {
    
    @Mock
    private JRaftServer raftServer;
    
    @Mock
    private CliService cliService;
    
    @Mock
    private Node node;
    
    private JRaftMaintainService maintainService;
    
    @BeforeEach
    void setUp() {
        maintainService = new JRaftMaintainService(raftServer);
    }
    
    @Test
    void testExecuteStringArrayReturnsNotSupport() {
        RestResult<String> result = maintainService.execute(new String[] {"a", "b"});
        assertFalse(result.ok());
        assertTrue(result.getData() == null || result.getMessage() != null);
    }
    
    @Test
    void testExecuteMapWithGroupIdWhenNodeIsNull() {
        when(raftServer.getCliService()).thenReturn(cliService);
        when(raftServer.findNodeByGroup("g1")).thenReturn(null);
        Map<String, String> args = new HashMap<>();
        args.put(JRaftConstants.GROUP_ID, "g1");
        args.put(JRaftConstants.COMMAND_NAME, "doSnapshot");
        
        RestResult<String> result = maintainService.execute(args);
        assertFalse(result.ok());
        assertTrue(result.getMessage().contains("not this raft group"));
        assertTrue(result.getMessage().contains("g1"));
    }
    
    @Test
    void testExecuteMapWithUnsupportedCommand() {
        when(raftServer.getCliService()).thenReturn(cliService);
        when(raftServer.findNodeByGroup("g1")).thenReturn(node);
        Map<String, String> args = new HashMap<>();
        args.put(JRaftConstants.GROUP_ID, "g1");
        args.put(JRaftConstants.COMMAND_NAME, "unknownCommand");
        
        RestResult<String> result = maintainService.execute(args);
        assertFalse(result.ok());
        assertTrue(result.getMessage().contains("Not support command"));
    }
    
    @Test
    void testExecuteMapWithGroupIdAndValidCommandSuccess() {
        Configuration conf = new Configuration();
        conf.addPeer(PeerId.parsePeer("127.0.0.1:7848"));
        NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setInitialConf(conf);
        when(node.getOptions()).thenReturn(nodeOptions);
        when(raftServer.getCliService()).thenReturn(cliService);
        when(raftServer.findNodeByGroup("g1")).thenReturn(node);
        when(cliService.snapshot(anyString(), any())).thenReturn(Status.OK());
        Map<String, String> args = new HashMap<>();
        args.put(JRaftConstants.GROUP_ID, "g1");
        args.put(JRaftConstants.COMMAND_NAME, "doSnapshot");
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:7848");
        
        RestResult<String> result = maintainService.execute(args);
        assertTrue(result.ok());
    }
    
    @Test
    void testExecuteMapWithoutGroupIdIteratesMultiRaftGroup() {
        Configuration conf = new Configuration();
        conf.addPeer(PeerId.parsePeer("127.0.0.1:7848"));
        NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setInitialConf(conf);
        when(node.getOptions()).thenReturn(nodeOptions);
        when(raftServer.getCliService()).thenReturn(cliService);
        JRaftServer.RaftGroupTuple tuple = new JRaftServer.RaftGroupTuple(node, null, null, null);
        when(raftServer.getMultiRaftGroup()).thenReturn(Collections.singletonMap("g1", tuple));
        when(cliService.snapshot(anyString(), any())).thenReturn(Status.OK());
        Map<String, String> args = new HashMap<>();
        args.put(JRaftConstants.COMMAND_NAME, "doSnapshot");
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:7848");
        
        RestResult<String> result = maintainService.execute(args);
        assertTrue(result.ok());
    }
    
    @Test
    void testExecuteMapSingleThrowsCausesFailedResult() {
        when(raftServer.getCliService()).thenReturn(cliService);
        when(raftServer.findNodeByGroup("g1")).thenReturn(node);
        when(node.getOptions()).thenThrow(new RuntimeException("options error"));
        Map<String, String> args = new HashMap<>();
        args.put(JRaftConstants.GROUP_ID, "g1");
        args.put(JRaftConstants.COMMAND_NAME, "doSnapshot");
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:7848");
        
        RestResult<String> result = maintainService.execute(args);
        assertFalse(result.ok());
        assertTrue(result.getMessage().contains("options error"));
    }
    
    /**
     * When args has no GROUP_ID, execute iterates getMultiRaftGroup(). If one group's single() returns
     * failure, execute returns that result immediately (lines 64-66). Use a tuple with null node so
     * single() returns "not this raft group".
     */
    @Test
    void testExecuteMapWithoutGroupIdReturnsFirstFailedResult() {
        when(raftServer.getCliService()).thenReturn(cliService);
        JRaftServer.RaftGroupTuple tupleWithNullNode =
            new JRaftServer.RaftGroupTuple(null, null, null, null);
        when(raftServer.getMultiRaftGroup())
            .thenReturn(Collections.singletonMap("g1", tupleWithNullNode));
        Map<String, String> args = new HashMap<>();
        args.put(JRaftConstants.COMMAND_NAME, "doSnapshot");
        args.put(JRaftConstants.COMMAND_VALUE, "127.0.0.1:7848");
        
        RestResult<String> result = maintainService.execute(args);
        assertFalse(result.ok());
        assertTrue(result.getMessage().contains("not this raft group"));
        assertTrue(result.getMessage().contains("g1"));
    }
}
