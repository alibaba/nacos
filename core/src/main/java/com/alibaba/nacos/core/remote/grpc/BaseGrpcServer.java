/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.grpc.negotiator.NacosGrpcProtocolNegotiator;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.netty.shaded.io.grpc.netty.InternalProtocolNegotiator;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.util.MutableHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Grpc implementation as a rpc server.
 *
 * @author liuzunfei
 * @version $Id: BaseGrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
public abstract class BaseGrpcServer extends BaseRpcServer {
    
    /**
     * The ProtocolNegotiator instance used for communication.
     */
    protected NacosGrpcProtocolNegotiator protocolNegotiator;
    
    private Server server;
    
    @Autowired
    private GrpcRequestAcceptor grpcCommonRequestAcceptor;
    
    @Autowired
    private GrpcBiStreamRequestAcceptor grpcBiStreamRequestAcceptor;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.GRPC;
    }
    
    @Override
    public void startServer() throws Exception {
        final MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();
        addServices(handlerRegistry, getSeverInterceptors().toArray(new ServerInterceptor[0]));
        NettyServerBuilder builder = NettyServerBuilder.forPort(getServicePort()).executor(getRpcExecutor());
        
        Optional<InternalProtocolNegotiator.ProtocolNegotiator> negotiator = newProtocolNegotiator();
        if (negotiator.isPresent()) {
            InternalProtocolNegotiator.ProtocolNegotiator actual = negotiator.get();
            Loggers.REMOTE.info("Add protocol negotiator {}", actual.getClass().getCanonicalName());
            builder.protocolNegotiator(actual);
        }
        
        for (ServerTransportFilter each : getServerTransportFilters()) {
            builder.addTransportFilter(each);
        }
        server = builder.maxInboundMessageSize(getMaxInboundMessageSize()).fallbackHandlerRegistry(handlerRegistry)
                .compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .keepAliveTime(getKeepAliveTime(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                .permitKeepAliveTime(getPermitKeepAliveTime(), TimeUnit.MILLISECONDS).build();
        
        server.start();
    }
    
    @Override
    public void reloadProtocolContext() {
        reloadProtocolNegotiator();
    }
    
    /**
     * Build new one protocol negotiator.
     *
     * <p>Such as support tls, proxy protocol and so on</p>
     *
     * @return ProtocolNegotiator
     */
    protected Optional<InternalProtocolNegotiator.ProtocolNegotiator> newProtocolNegotiator() {
        return Optional.empty();
    }
    
    /**
     * reload protocol negotiator If necessary.
     */
    public void reloadProtocolNegotiator() {
        if (protocolNegotiator != null) {
            try {
                protocolNegotiator.reloadNegotiator();
            } catch (Throwable throwable) {
                Loggers.REMOTE.info("Nacos {} Rpc server reload negotiator fail at port {}.",
                        this.getClass().getSimpleName(), getServicePort());
                throw throwable;
            }
        }
    }
    
    protected long getPermitKeepAliveTime() {
        return GrpcServerConstants.GrpcConfig.DEFAULT_GRPC_PERMIT_KEEP_ALIVE_TIME;
    }
    
    protected long getKeepAliveTime() {
        return GrpcServerConstants.GrpcConfig.DEFAULT_GRPC_KEEP_ALIVE_TIME;
    }
    
    protected long getKeepAliveTimeout() {
        return GrpcServerConstants.GrpcConfig.DEFAULT_GRPC_KEEP_ALIVE_TIMEOUT;
    }
    
    protected int getMaxInboundMessageSize() {
        Integer property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.MAX_INBOUND_MSG_SIZE_PROPERTY,
                Integer.class);
        if (property != null) {
            return property;
        }
        return GrpcServerConstants.GrpcConfig.DEFAULT_GRPC_MAX_INBOUND_MSG_SIZE;
    }
    
    protected List<ServerInterceptor> getSeverInterceptors() {
        List<ServerInterceptor> result = new LinkedList<>();
        result.add(new GrpcConnectionInterceptor());
        return result;
    }
    
    protected List<ServerTransportFilter> getServerTransportFilters() {
        return Collections.singletonList(new AddressTransportFilter(connectionManager));
    }
    
    private void addServices(MutableHandlerRegistry handlerRegistry, ServerInterceptor... serverInterceptor) {
        
        // unary common call register.
        final MethodDescriptor<Payload, Payload> unaryPayloadMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName(
                        MethodDescriptor.generateFullMethodName(GrpcServerConstants.REQUEST_SERVICE_NAME,
                                GrpcServerConstants.REQUEST_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
        
        final ServerCallHandler<Payload, Payload> payloadHandler = ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> grpcCommonRequestAcceptor.request(request, responseObserver));
        
        final ServerServiceDefinition serviceDefOfUnaryPayload = ServerServiceDefinition.builder(
                GrpcServerConstants.REQUEST_SERVICE_NAME).addMethod(unaryPayloadMethod, payloadHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfUnaryPayload, serverInterceptor));
        
        // bi stream register.
        final ServerCallHandler<Payload, Payload> biStreamHandler = ServerCalls.asyncBidiStreamingCall(
                (responseObserver) -> grpcBiStreamRequestAcceptor.requestBiStream(responseObserver));
        
        final MethodDescriptor<Payload, Payload> biStreamMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING).setFullMethodName(
                        MethodDescriptor.generateFullMethodName(GrpcServerConstants.REQUEST_BI_STREAM_SERVICE_NAME,
                                GrpcServerConstants.REQUEST_BI_STREAM_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
        
        final ServerServiceDefinition serviceDefOfBiStream = ServerServiceDefinition.builder(
                GrpcServerConstants.REQUEST_BI_STREAM_SERVICE_NAME).addMethod(biStreamMethod, biStreamHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfBiStream, serverInterceptor));
        
    }
    
    @Override
    public void shutdownServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }
    
    /**
     * get rpc executor.
     *
     * @return executor.
     */
    public abstract ThreadPoolExecutor getRpcExecutor();
    
}
