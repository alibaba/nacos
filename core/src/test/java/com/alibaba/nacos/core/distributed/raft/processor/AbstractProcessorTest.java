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

import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.raft.JRaftServer;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosure;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.rpc.Connection;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.google.protobuf.Message;
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
class AbstractProcessorTest {
    
    @Mock
    private JRaftServer serverWithNullTuple;
    
    @Mock
    private JRaftServer serverWithFollowerNode;
    
    @Mock
    private Node followerNode;
    
    private JRaftServer server = new JRaftServer() {
        
        @Override
        public void applyOperation(Node node, Message data, FailoverClosure closure) {
            closure.setResponse(Response.newBuilder().setSuccess(false)
                .setErrMsg("Error message transmission").build());
            closure.run(new Status(RaftError.UNKNOWN, "Error message transmission"));
        }
    };
    
    @Test
    void testErrorThroughRpc() {
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
        AbstractProcessor processor = new NacosWriteRequestProcessor(server);
        processor.execute(server, context, WriteRequest.newBuilder().build(),
            new JRaftServer.RaftGroupTuple());
        
        Response response = reference.get();
        assertNotNull(response);
        
        assertEquals("Error message transmission", response.getErrMsg());
        assertFalse(response.getSuccess());
    }
    
    @Test
    void testHandleRequestWhenTupleNotFound() {
        when(serverWithNullTuple.findTupleByGroup(anyString())).thenReturn(null);
        NacosWriteRequestProcessor processor = new NacosWriteRequestProcessor(serverWithNullTuple);
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
        processor.handleRequest(context,
            WriteRequest.newBuilder().setGroup("unknown-group").build());
        Response response = reference.get();
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("Could not find the corresponding Raft Group"));
        assertTrue(response.getErrMsg().contains("unknown-group"));
    }
    
    @Test
    void testHandleRequestWhenNodeIsNotLeader() {
        when(followerNode.isLeader()).thenReturn(false);
        JRaftServer.RaftGroupTuple tuple =
            new JRaftServer.RaftGroupTuple(followerNode, null, null, null);
        when(serverWithFollowerNode.findTupleByGroup(anyString())).thenReturn(tuple);
        NacosWriteRequestProcessor processor =
            new NacosWriteRequestProcessor(serverWithFollowerNode);
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
        processor.handleRequest(context, WriteRequest.newBuilder().setGroup("g").build());
        Response response = reference.get();
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("Could not find leader"));
        assertTrue(response.getErrMsg().contains("g"));
    }
    
    @Test
    void testHandleRequestWhenThrowableInTryBlockSendsErrorResponse() {
        when(followerNode.isLeader()).thenThrow(new RuntimeException("node error"));
        JRaftServer.RaftGroupTuple tuple =
            new JRaftServer.RaftGroupTuple(followerNode, null, null, null);
        when(serverWithFollowerNode.findTupleByGroup(anyString())).thenReturn(tuple);
        NacosWriteRequestProcessor processor =
            new NacosWriteRequestProcessor(serverWithFollowerNode);
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
        processor.handleRequest(context, WriteRequest.newBuilder().setGroup("g").build());
        Response response = reference.get();
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("node error"));
    }
    
    @Test
    void testExecuteSuccessPathSendsResponseData() {
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
        NacosWriteRequestProcessor processor =
            new NacosWriteRequestProcessor(serverWithFollowerNode);
        processor.handleRequest(context, WriteRequest.newBuilder().setGroup("g").build());
        Response response = reference.get();
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }
    
    @Test
    void testExecuteWhenClosureSetThrowableSendsErrorResponseViaRpcContext() {
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
        when(followerNode.isLeader()).thenReturn(true);
        JRaftServer.RaftGroupTuple tuple =
            new JRaftServer.RaftGroupTuple(followerNode, null, null, null);
        when(serverWithFollowerNode.findTupleByGroup(anyString())).thenReturn(tuple);
        doAnswer(invocation -> {
            FailoverClosure c = invocation.getArgument(2);
            c.setThrowable(new RuntimeException("execute exception branch"));
            c.run(Status.OK());
            return null;
        }).when(serverWithFollowerNode).applyOperation(any(), any(), any());
        NacosWriteRequestProcessor processor =
            new NacosWriteRequestProcessor(serverWithFollowerNode);
        processor.handleRequest(context, WriteRequest.newBuilder().setGroup("g").build());
        Response response = reference.get();
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("execute exception branch"));
    }
    
}
