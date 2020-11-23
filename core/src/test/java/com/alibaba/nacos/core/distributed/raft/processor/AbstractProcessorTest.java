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

import com.alibaba.nacos.consistency.SerializeFactory;
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
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class AbstractProcessorTest {
    
    private JRaftServer server = new JRaftServer() {
        @Override
        public void applyOperation(Node node, Message data, FailoverClosure closure) {
            closure.setResponse(
                    Response.newBuilder().setSuccess(false).setErrMsg("Error message transmission").build());
            closure.run(new Status(RaftError.UNKNOWN, "Error message transmission"));
        }
    };
    
    @Test
    public void testErrorThroughRpc() {
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
        AbstractProcessor processor = new NacosLogProcessor(server, SerializeFactory.getDefault());
        processor.execute(server, context, WriteRequest.newBuilder().build(), new JRaftServer.RaftGroupTuple());
        
        Response response = reference.get();
        Assert.assertNotNull(response);
        
        Assert.assertEquals("Error message transmission", response.getErrMsg());
        Assert.assertFalse(response.getSuccess());
    }
    
}
