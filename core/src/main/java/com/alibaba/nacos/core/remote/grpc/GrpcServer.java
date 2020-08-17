/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.grpc.GrpcMetadata;
import com.alibaba.nacos.api.grpc.GrpcRequest;
import com.alibaba.nacos.api.grpc.GrpcResponse;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.remote.RpcServer;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.util.MutableHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * Grpc implementation as  a rpc server.
 *
 * @author liuzunfei
 * @version $Id: GrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
@Service
public class GrpcServer extends RpcServer {
    
    private static final int PORT_OFFSET = 1000;
    
    private Server server;
    
    @Autowired
    private GrpcStreamRequestHanderImpl streamRequestHander;
    
    @Autowired
    private GrpcRequestHandlerReactor requestHander;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;
    
    int grpcServerPort = ApplicationUtils.getPort() + rpcPortOffset();
    
    private void init() {
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.GRPC;
    }
    
    @Override
    public void startServer() throws Exception {
        init();
        final MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();
    
        // server intercetpor to set connection id.
        ServerInterceptor serverInterceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                final Context ctx = Context.current()
                        .withValue(CONTEXT_KEY_CONN_ID, call.getAttributes().get(TRANS_KEY_CONN_ID));
                return Contexts.interceptCall(ctx, call, headers, next);
            }
        };
    
        addServices(handlerRegistry, serverInterceptor);
        server = ServerBuilder.forPort(grpcServerPort).fallbackHandlerRegistry(handlerRegistry)
                .addTransportFilter(new ServerTransportFilter() {
                    @Override
                    public Attributes transportReady(Attributes transportAttrs) {
                        Attributes test = transportAttrs.toBuilder()
                                .set(TRANS_KEY_CONN_ID, UUID.randomUUID().toString()).build();
                        return test;
                    }
    
                    @Override
                    public void transportTerminated(Attributes transportAttrs) {
                        String connectionid = transportAttrs.get(TRANS_KEY_CONN_ID);
                        connectionManager.unregister(connectionid);
                    }
                }).build();
        server.start();
    }
    
    private void addServices(MutableHandlerRegistry handlerRegistry, ServerInterceptor... serverInterceptor) {
        
        // unary call register.
        final MethodDescriptor<GrpcRequest, GrpcResponse> unaryMethod = MethodDescriptor.<GrpcRequest, GrpcResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("Request", "request"))
                .setRequestMarshaller(ProtoUtils.marshaller(GrpcRequest.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(GrpcResponse.getDefaultInstance())).build();
        
        final ServerCallHandler<GrpcRequest, GrpcResponse> handler = ServerCalls
                .asyncUnaryCall((request, responseObserver) -> {
                    GrpcMetadata grpcMetadata = request.getMetadata().toBuilder()
                            .setConnectionId(CONTEXT_KEY_CONN_ID.get()).build();
                    GrpcRequest requestNew = request.toBuilder().setMetadata(grpcMetadata).build();
                    requestHander.request(requestNew, responseObserver);
                });
        
        final ServerServiceDefinition serviceDefOfUnary = ServerServiceDefinition.builder("Request")
                .addMethod(unaryMethod, handler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfUnary, serverInterceptor));
        
        // server stream register.
        final ServerCallHandler<GrpcRequest, GrpcResponse> streamHandler = ServerCalls
                .asyncServerStreamingCall((request, responseObserver) -> {
                    GrpcMetadata grpcMetadata = request.getMetadata().toBuilder()
                            .setConnectionId(CONTEXT_KEY_CONN_ID.get()).build();
                    GrpcRequest requestNew = request.toBuilder().setMetadata(grpcMetadata).build();
                    streamRequestHander.requestStream(requestNew, responseObserver);
                });
        
        final MethodDescriptor<GrpcRequest, GrpcResponse> serverStreamMethod = MethodDescriptor.<GrpcRequest, GrpcResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("RequestStream", "requestStream"))
                .setRequestMarshaller(ProtoUtils.marshaller(GrpcRequest.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(GrpcResponse.getDefaultInstance())).build();
        
        final ServerServiceDefinition serviceDefOfServerStream = ServerServiceDefinition.builder("RequestStream")
                .addMethod(serverStreamMethod, streamHandler).build();
        
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfServerStream, serverInterceptor));
        
    }
    
    @Override
    public int rpcPortOffset() {
        return PORT_OFFSET;
    }
    
    @Override
    public void shundownServer() {
        if (server != null) {
            server.shutdown();
        }
    }
    
    static final Attributes.Key<String> TRANS_KEY_CONN_ID = Attributes.Key.create("conn_id");
    
    static final Context.Key<String> CONTEXT_KEY_CONN_ID = Context.key("conn_id");
    
}
