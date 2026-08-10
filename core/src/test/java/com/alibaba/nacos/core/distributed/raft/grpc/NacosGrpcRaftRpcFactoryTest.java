/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.raft.grpc;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.util.JRaftServiceLoader;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosGrpcRaftRpcFactoryTest {
    
    @Test
    void testCreatesNacosGrpcClientAndAppliesHelper() {
        NacosGrpcRaftRpcFactory factory = new NacosGrpcRaftRpcFactory();
        factory.registerProtobufSerializer(ReadRequest.class.getName(),
            ReadRequest.getDefaultInstance());
        factory.getMarshallerRegistry().registerResponseInstance(ReadRequest.class.getName(),
            Response.getDefaultInstance());
        AtomicBoolean helperCalled = new AtomicBoolean(false);
        
        RpcClient rpcClient = factory.createRpcClient(client -> helperCalled.set(true));
        
        assertInstanceOf(NacosGrpcClient.class, rpcClient);
        assertTrue(helperCalled.get());
    }
    
    @Test
    void testNacosFactoryHasHighestSpiPriority() {
        RaftRpcFactory factory = JRaftServiceLoader.load(RaftRpcFactory.class).first();
        
        assertInstanceOf(NacosGrpcRaftRpcFactory.class, factory);
        assertInstanceOf(NacosGrpcRaftRpcFactory.class, RpcFactoryHelper.rpcFactory());
    }
}
