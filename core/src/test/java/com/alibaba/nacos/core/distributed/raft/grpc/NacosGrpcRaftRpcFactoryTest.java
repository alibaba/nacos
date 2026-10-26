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
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.rpc.RpcProcessor;
import com.alipay.sofa.jraft.rpc.impl.GrpcServer;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.JRaftServiceLoader;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    
    @Test
    void testGrpcServerBindsEndpointAndHandlesRegisteredMessagesWithoutBolt() throws Exception {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("com.alipay.remoting.util.StringUtils"));
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("com.alipay.hessian.clhm.ConcurrentLinkedHashMap"));
        NacosGrpcRaftRpcFactory factory = new NacosGrpcRaftRpcFactory();
        Endpoint endpoint = new Endpoint("127.0.0.1", availablePort());
        AtomicInteger intercepted = new AtomicInteger();
        AtomicReference<ReadRequest> received = new AtomicReference<>();
        GrpcServer server = (GrpcServer) factory.createRpcServer(endpoint, rpcServer -> {
            assertFalse(rpcServer.isStarted());
            ((GrpcServer) rpcServer).addServerInterceptor(new ServerInterceptor() {
                
                @Override
                public <Q, S> ServerCall.Listener<Q> interceptCall(ServerCall<Q, S> call,
                    Metadata headers, ServerCallHandler<Q, S> next) {
                    intercepted.incrementAndGet();
                    return next.startCall(call, headers);
                }
            });
        });
        ManagedChannel channel = null;
        try {
            // Parsers registered after server construction must also be visible.
            factory.registerProtobufSerializer(ReadRequest.class.getName(),
                ReadRequest.getDefaultInstance());
            factory.getMarshallerRegistry().registerResponseInstance(ReadRequest.class.getName(),
                Response.getDefaultInstance());
            Response expected = Response.newBuilder().setSuccess(true).build();
            server.registerProcessor(new RpcProcessor<ReadRequest>() {
                
                @Override
                public void handleRequest(RpcContext context, ReadRequest request) {
                    received.set(request);
                    context.sendResponse(expected);
                }
                
                @Override
                public String interest() {
                    return ReadRequest.class.getName();
                }
            });
            assertTrue(server.init(null));
            InetSocketAddress address =
                (InetSocketAddress) server.getServer().getListenSockets().get(0);
            assertEquals(InetAddress.getByName(endpoint.getIp()), address.getAddress());
            assertEquals(endpoint.getPort(), server.boundPort());
            channel = NettyChannelBuilder.forAddress(endpoint.getIp(), endpoint.getPort())
                .usePlaintext().build();
            MethodDescriptor<ReadRequest, Response> method =
                MethodDescriptor.<ReadRequest, Response>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(
                        ReadRequest.class.getName(), "_call"))
                    .setRequestMarshaller(ProtoUtils.marshaller(ReadRequest.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Response.getDefaultInstance()))
                    .build();
            ReadRequest request = ReadRequest.newBuilder().setGroup("factory-test").build();
            Response response = ClientCalls.blockingUnaryCall(channel, method,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), request);
            assertEquals(expected, response);
            assertEquals(request, received.get());
            assertEquals(1, intercepted.get());
        } finally {
            if (channel != null) {
                channel.shutdownNow();
                channel.awaitTermination(5, TimeUnit.SECONDS);
            }
            server.shutdown();
        }
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void testBlankEndpointAddressBindsWildcard(String ip) throws Exception {
        NacosGrpcRaftRpcFactory factory = new NacosGrpcRaftRpcFactory();
        GrpcServer server =
            (GrpcServer) factory.createRpcServer(new Endpoint(ip, availablePort()), null);
        try {
            assertTrue(server.init(null));
            InetSocketAddress address =
                (InetSocketAddress) server.getServer().getListenSockets().get(0);
            assertTrue(address.getAddress().isAnyLocalAddress());
        } finally {
            server.shutdown();
        }
    }
    
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65535, 65536})
    void testInvalidEndpointPortIsRejected(int port) {
        NacosGrpcRaftRpcFactory factory = new NacosGrpcRaftRpcFactory();
        assertThrows(IllegalArgumentException.class,
            () -> factory.createRpcServer(new Endpoint("127.0.0.1", port), null));
    }
    
    @Test
    void testNullEndpointIsRejected() {
        NacosGrpcRaftRpcFactory factory = new NacosGrpcRaftRpcFactory();
        assertThrows(NullPointerException.class, () -> factory.createRpcServer(null, null));
    }
    
    private int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
