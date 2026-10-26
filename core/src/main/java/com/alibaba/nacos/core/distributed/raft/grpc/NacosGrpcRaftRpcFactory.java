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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.rpc.impl.GrpcRaftRpcFactory;
import com.alipay.sofa.jraft.rpc.impl.GrpcServer;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.SPI;
import com.alipay.sofa.jraft.util.SystemPropertyUtil;
import com.google.protobuf.Message;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.util.MutableHandlerRegistry;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos JRaft gRPC factory that installs {@link NacosGrpcClient} with a higher SPI priority.
 *
 * @author xiweng.yy
 */
@SPI(priority = 100)
public class NacosGrpcRaftRpcFactory extends GrpcRaftRpcFactory {
    
    private static final int MAX_INBOUND_MESSAGE_SIZE = SystemPropertyUtil.getInt(
        "jraft.grpc.max_inbound_message_size.bytes", 4 * 1024 * 1024);
    
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
    
    /**
     * Creates a gRPC server without the optional Bolt transport dependency.
     *
     * <p>JRaft 1.4.1's factory references Bolt's StringUtils when choosing the
     * listen address. Nacos excludes Bolt, so construct the server here while
     * preserving the endpoint binding, message limit and configuration helper.
     *
     * @param endpoint the server listen endpoint
     * @param helper optional server configuration applied before startup
     * @return the configured JRaft gRPC server
     */
    @Override
    public RpcServer createRpcServer(Endpoint endpoint, ConfigHelper<RpcServer> helper) {
        int port = Objects.requireNonNull(endpoint, "endpoint").getPort();
        if (port <= 0 || port >= 0xFFFF) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
        InetSocketAddress listenAddress = StringUtils.isBlank(endpoint.getIp())
            ? new InetSocketAddress(port) : new InetSocketAddress(endpoint.getIp(), port);
        MutableHandlerRegistry registry = new MutableHandlerRegistry();
        NettyServerBuilder builder = NettyServerBuilder.forAddress(listenAddress)
            .directExecutor().fallbackHandlerRegistry(registry)
            .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE);
        RpcServer server =
            new GrpcServer(builder.build(), registry, parserClasses, getMarshallerRegistry());
        if (helper != null) {
            helper.config(server);
        }
        return server;
    }
}
