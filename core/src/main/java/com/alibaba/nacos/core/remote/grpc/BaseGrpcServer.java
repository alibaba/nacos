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
import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.internal.ServerStream;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.util.MutableHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetSocketAddress;

/**
 * Grpc implementation as  a rpc server.
 *
 * @author liuzunfei
 * @version $Id: BaseGrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
public abstract class BaseGrpcServer extends BaseRpcServer {
    
    private Server server;
    
    private static final String REQUEST_BI_STREAM_SERVICE_NAME = "BiRequestStream";
    
    private static final String REQUEST_BI_STREAM_METHOD_NAME = "requestBiStream";
    
    private static final String REQUEST_SERVICE_NAME = "Request";
    
    private static final String REQUEST_METHOD_NAME = "request";
    
    @Autowired
    private GrpcRequestAcceptor grpcCommonRequestAcceptor;
    
    @Autowired
    private GrpcBiStreamRequestAcceptor grpcBiStreamRequestAcceptor;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;
    
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
            public <T, S> ServerCall.Listener<T> interceptCall(ServerCall<T, S> call, Metadata headers,
                    ServerCallHandler<T, S> next) {
                Context ctx = Context.current()
                        .withValue(CONTEXT_KEY_CONN_ID, call.getAttributes().get(TRANS_KEY_CONN_ID))
                        .withValue(CONTEXT_KEY_CONN_CLIENT_IP, call.getAttributes().get(TRANS_KEY_CLIENT_IP))
                        .withValue(CONTEXT_KEY_CONN_CLIENT_PORT, call.getAttributes().get(TRANS_KEY_CLIENT_PORT))
                        .withValue(CONTEXT_KEY_CONN_LOCAL_PORT, call.getAttributes().get(TRANS_KEY_LOCAL_PORT));
                if (REQUEST_BI_STREAM_SERVICE_NAME.equals(call.getMethodDescriptor().getServiceName())) {
                    Channel internalChannel = getInternalChannel(call);
                    ctx = ctx.withValue(CONTEXT_KEY_CHANNEL, internalChannel);
                }
                return Contexts.interceptCall(ctx, call, headers, next);
            }
        };
    
        addServices(handlerRegistry, serverInterceptor);
    
        server = NettyServerBuilder.forPort(getServicePort()).fallbackHandlerRegistry(handlerRegistry)
                .addTransportFilter(new ServerTransportFilter() {
                    @Override
                    public Attributes transportReady(Attributes transportAttrs) {
                        InetSocketAddress remoteAddress = (InetSocketAddress) transportAttrs
                                .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                        InetSocketAddress localAddress = (InetSocketAddress) transportAttrs
                                .get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
                        String remoteIp = remoteAddress.getAddress().getHostAddress().toString();
                        int remotePort = remoteAddress.getPort();
                        int localPort = localAddress.getPort();
                        Attributes attrWraper = transportAttrs.toBuilder()
                                .set(TRANS_KEY_CONN_ID, UuidUtils.generateUuid()).set(TRANS_KEY_CLIENT_IP, remoteIp)
                                .set(TRANS_KEY_CLIENT_PORT, remotePort).set(TRANS_KEY_LOCAL_PORT, localPort).build();
                        String connectionid = attrWraper.get(TRANS_KEY_CONN_ID);
                        Loggers.REMOTE.info(" connection transportReady,connectionid = {} ", connectionid);
                        return attrWraper;
    
                    }
    
                    @Override
                    public void transportTerminated(Attributes transportAttrs) {
                        String connectionid = transportAttrs.get(TRANS_KEY_CONN_ID);
                        Loggers.REMOTE.info(" connection transportTerminated,connectionid = {} ", connectionid);
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
                .setFullMethodName(MethodDescriptor.generateFullMethodName(REQUEST_SERVICE_NAME, REQUEST_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
    
        final ServerCallHandler<Payload, Payload> payloadHandler = ServerCalls
                .asyncUnaryCall((request, responseObserver) -> {
                    com.alibaba.nacos.api.grpc.auto.Metadata grpcMetadata = request.getMetadata().toBuilder()
                            .setConnectionId(CONTEXT_KEY_CONN_ID.get()).setClientIp(CONTEXT_KEY_CONN_CLIENT_IP.get())
                            .setClientPort(CONTEXT_KEY_CONN_CLIENT_PORT.get()).build();
                    Payload requestNew = request.toBuilder().setMetadata(grpcMetadata).build();
                    grpcCommonRequestAcceptor.request(requestNew, responseObserver);
                });
    
        final ServerServiceDefinition serviceDefOfUnaryPayload = ServerServiceDefinition.builder(REQUEST_SERVICE_NAME)
                .addMethod(unarypayloadMethod, payloadHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfUnaryPayload, serverInterceptor));
    
        // bi stream register.
        final ServerCallHandler<Payload, Payload> biStreamHandler = ServerCalls
                .asyncBidiStreamingCall((responseObserver) -> {
                    return grpcBiStreamRequestAcceptor.requestBiStream(responseObserver);
                });
    
        final MethodDescriptor<Payload, Payload> biStreamMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING).setFullMethodName(MethodDescriptor
                        .generateFullMethodName(REQUEST_BI_STREAM_SERVICE_NAME, REQUEST_BI_STREAM_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
    
        final ServerServiceDefinition serviceDefOfBiStream = ServerServiceDefinition
                .builder(REQUEST_BI_STREAM_SERVICE_NAME).addMethod(biStreamMethod, biStreamHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfBiStream, serverInterceptor));
        
    }
    
    @Override
    public void shundownServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }
    
    static final Attributes.Key<String> TRANS_KEY_CONN_ID = Attributes.Key.create("conn_id");
    
    static final Attributes.Key<String> TRANS_KEY_CLIENT_IP = Attributes.Key.create("client_ip");
    
    static final Attributes.Key<Integer> TRANS_KEY_CLIENT_PORT = Attributes.Key.create("client_port");
    
    static final Attributes.Key<Integer> TRANS_KEY_LOCAL_PORT = Attributes.Key.create("local_port");
    
    static final Context.Key<String> CONTEXT_KEY_CONN_ID = Context.key("conn_id");
    
    static final Context.Key<String> CONTEXT_KEY_CONN_CLIENT_IP = Context.key("client_ip");
    
    static final Context.Key<Integer> CONTEXT_KEY_CONN_CLIENT_PORT = Context.key("client_port");
    
    static final Context.Key<Integer> CONTEXT_KEY_CONN_LOCAL_PORT = Context.key("local_port");
    
    static final Context.Key<Channel> CONTEXT_KEY_CHANNEL = Context.key("ctx_channel");
    
}
