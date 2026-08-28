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

import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.impl.GrpcRaftRpcFactory;
import com.alipay.sofa.jraft.util.SPI;
import com.google.protobuf.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos JRaft gRPC factory that installs {@link NacosGrpcClient} with a higher SPI priority.
 *
 * @author xiweng.yy
 */
@SPI(priority = 100)
public class NacosGrpcRaftRpcFactory extends GrpcRaftRpcFactory {
    
    private final Map<String, Message> parserClasses = new ConcurrentHashMap<>();
    
    @Override
    public void registerProtobufSerializer(String className, Object... args) {
        super.registerProtobufSerializer(className, args);
        parserClasses.put(className, (Message) args[0]);
    }
    
    @Override
    public RpcClient createRpcClient(RaftRpcFactory.ConfigHelper<RpcClient> helper) {
        RpcClient rpcClient = new NacosGrpcClient(parserClasses, getMarshallerRegistry());
        if (helper != null) {
            helper.config(rpcClient);
        }
        return rpcClient;
    }
}
