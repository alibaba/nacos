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

package com.alibaba.nacos.core.distributed.raft.processor;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.distributed.raft.JRaftServer;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosure;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.rpc.Connection;
import com.alipay.sofa.jraft.rpc.RpcContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosReadRequestProcessorTest {
    
    @Mock
    private JRaftServer serverWithNullTuple;
    
    @Mock
    private JRaftServer serverWithFollowerNode;
    
    @Mock
    private Node followerNode;
    
    @Test
    void testInterestReturnsReadRequestClassName() {
        NacosReadRequestProcessor processor = new NacosReadRequestProcessor(serverWithNullTuple);
        assertEquals(ReadRequest.class.getName(), processor.interest());
    }
    
    @Test
    void testHandleRequestWhenTupleNotFound() {
        when(serverWithNullTuple.findTupleByGroup(anyString())).thenReturn(null);
        NacosReadRequestProcessor processor = new NacosReadRequestProcessor(serverWithNullTuple);
        final AtomicReference<Response> reference = new AtomicReference<>();
        RpcContext context = new RpcContext() {
            
            @Override
            public void sendResponse(Object responseObj) {
                reference.set((Response) responseObj);
            }
            
            @Override
            public Connection getConnection() {
                return null;
            }
            
            @Override
            public String getRemoteAddress() {
                return null;
            }
        };
        processor.handleRequest(context, ReadRequest.newBuilder().setGroup("unknown").build());
        Response response = reference.get();
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("Could not find the corresponding Raft Group"));
    }
    
    @Test
    void testHandleRequestWhenNodeIsNotLeader() {
        when(followerNode.isLeader()).thenReturn(false);
        JRaftServer.RaftGroupTuple tuple =
            new JRaftServer.RaftGroupTuple(followerNode, null, null, null);
        when(serverWithFollowerNode.findTupleByGroup(anyString())).thenReturn(tuple);
        NacosReadRequestProcessor processor = new NacosReadRequestProcessor(serverWithFollowerNode);
        final AtomicReference<Response> reference = new AtomicReference<>();
        RpcContext context = new RpcContext() {
            
            @Override
            public void sendResponse(Object responseObj) {
                reference.set((Response) responseObj);
            }
            
            @Override
            public Connection getConnection() {
                return null;
            }
            
            @Override
            public String getRemoteAddress() {
                return null;
            }
        };
        processor.handleRequest(context, ReadRequest.newBuilder().setGroup("g").build());
        Response response = reference.get();
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("Could not find leader"));
    }
    
    @Test
    void testHandleRequestWhenLeaderCallsExecute() {
        when(followerNode.isLeader()).thenReturn(true);
        JRaftServer.RaftGroupTuple tuple =
            new JRaftServer.RaftGroupTuple(followerNode, null, null, null);
        when(serverWithFollowerNode.findTupleByGroup(anyString())).thenReturn(tuple);
        doAnswer(invocation -> {
            FailoverClosure c = invocation.getArgument(2);
            c.setResponse(Response.newBuilder().setSuccess(true).build());
            c.run(Status.OK());
            return null;
        }).when(serverWithFollowerNode).applyOperation(any(), any(), any());
        NacosReadRequestProcessor processor = new NacosReadRequestProcessor(serverWithFollowerNode);
        final AtomicReference<Response> reference = new AtomicReference<>();
        RpcContext context = new RpcContext() {
            
            @Override
            public void sendResponse(Object responseObj) {
                reference.set((Response) responseObj);
            }
            
            @Override
            public Connection getConnection() {
                return null;
            }
            
            @Override
            public String getRemoteAddress() {
                return null;
            }
        };
        processor.handleRequest(context, ReadRequest.newBuilder().setGroup("g").build());
        Response response = reference.get();
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }
}
