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

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.alibaba.nacos.common.utils.UuidUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.remote.RpcServer;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Contexts;
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
import io.grpc.internal.ServerStream;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.util.MutableHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private GrpcRequestAcceptor grpcCommonRequestAcceptor;
    
    @Autowired
    GrpcBiStreamRequestAcceptor grpcBiStreamRequestAcceptor;
    
    //    @Autowired
    //    private GrpcStreamRequestAcceptor grpcPaylodStreamRequestAcceptorImpl;
    //
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;
    
    int grpcServerPort = ApplicationUtils.getPort() + rpcPortOffset();
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.GRPC;
    }
    
    @Override
    public void startServer() throws Exception {
        final MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();
    
        // server intercetpor to set connection id.
        ServerInterceptor serverInterceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                Context ctx = Context.current()
                        .withValue(CONTEXT_KEY_CONN_ID, call.getAttributes().get(TRANS_KEY_CONN_ID));
                if ("RequestStream".equals(call.getMethodDescriptor().getServiceName())) {
                    Channel internalChannel = getInternalChannel(call);
                    ctx = ctx.withValue(CONTEXT_KEY_CHANNEL, internalChannel);
                }
                return Contexts.interceptCall(ctx, call, headers, next);
            }
        };
    
        addServices(handlerRegistry, serverInterceptor);
        server = ServerBuilder.forPort(grpcServerPort).fallbackHandlerRegistry(handlerRegistry)
                .addTransportFilter(new ServerTransportFilter() {
                    @Override
                    public Attributes transportReady(Attributes transportAttrs) {
                        Attributes test = transportAttrs.toBuilder().set(TRANS_KEY_CONN_ID, UuidUtils.generateUuid())
                                .build();
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
    
    private Channel getInternalChannel(ServerCall serverCall) {
        ServerStream serverStream = (ServerStream) ReflectUtils.getFieldValue(serverCall, "stream");
        Channel channel = (Channel) ReflectUtils.getFieldValue(serverStream, "channel");
        return channel;
    }
    
    private void addServices(MutableHandlerRegistry handlerRegistry, ServerInterceptor... serverInterceptor) {
    
        // unary common call register.
        final MethodDescriptor<Payload, Payload> unarypayloadMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("Request", "request"))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
    
        final ServerCallHandler<Payload, Payload> payloadHandler = ServerCalls
                .asyncUnaryCall((request, responseObserver) -> {
                    com.alibaba.nacos.api.grpc.auto.Metadata grpcMetadata = request.getMetadata().toBuilder()
                            .setConnectionId(CONTEXT_KEY_CONN_ID.get()).build();
                    Payload requestNew = request.toBuilder().setMetadata(grpcMetadata).build();
                    grpcCommonRequestAcceptor.request(requestNew, responseObserver);
                });
    
        final ServerServiceDefinition serviceDefOfUnaryPayload = ServerServiceDefinition.builder("Request")
                .addMethod(unarypayloadMethod, payloadHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfUnaryPayload, serverInterceptor));
    
        // server stream register.
        //        final ServerCallHandler<Payload, Payload> streamHandler = ServerCalls
        //                .asyncServerStreamingCall((request, responseObserver) -> {
        //                    com.alibaba.nacos.api.grpc.auto.Metadata grpcMetadata = request.getMetadata().toBuilder()
        //                            .setConnectionId(CONTEXT_KEY_CONN_ID.get()).build();
        //                    Payload requestNew = request.toBuilder().setMetadata(grpcMetadata).build();
        //                    grpcPaylodStreamRequestAcceptorImpl.requestStream(requestNew, responseObserver);
        //                });
        //
        //        final MethodDescriptor<Payload, Payload> serverStreamMethod = MethodDescriptor.<Payload, Payload>newBuilder()
        //                .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
        //                .setFullMethodName(MethodDescriptor.generateFullMethodName("RequestStream", "requestStream"))
        //                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
        //                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
        //
        //        final ServerServiceDefinition servicePayloadDefOfServerStream = ServerServiceDefinition.builder("RequestStream")
        //                .addMethod(serverStreamMethod, streamHandler).build();
        //        handlerRegistry.addService(ServerInterceptors.intercept(servicePayloadDefOfServerStream, serverInterceptor));
    
        // bi stream register.
        final ServerCallHandler<Payload, Payload> biStreamHandler = ServerCalls
                .asyncBidiStreamingCall((responseObserver) -> {
                    return grpcBiStreamRequestAcceptor.requestBiStream(responseObserver);
                });
    
        final MethodDescriptor<Payload, Payload> biStreamMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("BiRequestStream", "requestBiStream"))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
    
        final ServerServiceDefinition serviceDefOfBiStream = ServerServiceDefinition.builder("BiRequestStream")
                .addMethod(biStreamMethod, biStreamHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfBiStream, serverInterceptor));
        
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
    
    static final Context.Key<Channel> CONTEXT_KEY_CHANNEL = Context.key("ctx_channel");
    
}
