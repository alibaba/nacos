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
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.packagescan.resource.DefaultResourceLoader;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.packagescan.resource.ResourceLoader;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.util.MutableHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Grpc implementation as a rpc server.
 *
 * @author liuzunfei
 * @version $Id: BaseGrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
public abstract class BaseGrpcServer extends BaseRpcServer {

    private Server server;

    @Autowired
    private GrpcServerConfig grpcServerConfig;

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

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

    @JustForTest
    public void setGrpcServerConfig(GrpcServerConfig grpcServerConfig) {
        this.grpcServerConfig = grpcServerConfig;
    }

    @Override
    public void startServer() throws Exception {
        final MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();
        addServices(handlerRegistry, new GrpcConnectionInterceptor());
        NettyServerBuilder builder = NettyServerBuilder.forPort(getServicePort()).executor(getRpcExecutor());
        if (grpcServerConfig.getEnableSsl()) {
            if (grpcServerConfig.getCompatibility()) {
                builder.protocolNegotiator(new OptionalTlsProtocolNegotiator(getSslContextBuilder().build()));
            } else {
                builder.sslContext(getSslContextBuilder().build());
            }
        }

        server = builder.maxInboundMessageSize(getMaxInboundMessageSize()).fallbackHandlerRegistry(handlerRegistry)
                .compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .addTransportFilter(new AddressTransportFilter(connectionManager))
                .keepAliveTime(getKeepAliveTime(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                .permitKeepAliveTime(getPermitKeepAliveTime(), TimeUnit.MILLISECONDS)
                .build();
        
        server.start();
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
    
    private void addServices(MutableHandlerRegistry handlerRegistry, ServerInterceptor... serverInterceptor) {
        
        // unary common call register.
        final MethodDescriptor<Payload, Payload> unaryPayloadMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(GrpcServerConstants.REQUEST_SERVICE_NAME,
                        GrpcServerConstants.REQUEST_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
        
        final ServerCallHandler<Payload, Payload> payloadHandler = ServerCalls
                .asyncUnaryCall((request, responseObserver) -> grpcCommonRequestAcceptor.request(request, responseObserver));
        
        final ServerServiceDefinition serviceDefOfUnaryPayload = ServerServiceDefinition.builder(
                        GrpcServerConstants.REQUEST_SERVICE_NAME)
                .addMethod(unaryPayloadMethod, payloadHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfUnaryPayload, serverInterceptor));
        
        // bi stream register.
        final ServerCallHandler<Payload, Payload> biStreamHandler = ServerCalls.asyncBidiStreamingCall(
                (responseObserver) -> grpcBiStreamRequestAcceptor.requestBiStream(responseObserver));
        
        final MethodDescriptor<Payload, Payload> biStreamMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING).setFullMethodName(MethodDescriptor
                        .generateFullMethodName(GrpcServerConstants.REQUEST_BI_STREAM_SERVICE_NAME,
                                GrpcServerConstants.REQUEST_BI_STREAM_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();
        
        final ServerServiceDefinition serviceDefOfBiStream = ServerServiceDefinition
                .builder(GrpcServerConstants.REQUEST_BI_STREAM_SERVICE_NAME).addMethod(biStreamMethod, biStreamHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfBiStream, serverInterceptor));
        
    }

    private SslContextBuilder getSslContextBuilder() {

        if (StringUtils.isBlank(grpcServerConfig.getCertificateChainFile()) || StringUtils.isBlank(grpcServerConfig.getPrivateKeyFile())) {
            throw new IllegalArgumentException("Server certificateChainFile or privateKeyFile must be not null");
        }
        InputStream certificateChainFile = getInputStream(grpcServerConfig.getCertificateChainFile(), "CertificateChainFile");
        InputStream privateKeyFile = getInputStream(grpcServerConfig.getPrivateKeyFile(), "privateKeyFile");
        SslContextBuilder sslClientContextBuilder = SslContextBuilder.forServer(certificateChainFile, privateKeyFile, grpcServerConfig.getPassword());

        if (StringUtils.isNotBlank(grpcServerConfig.getProtocols())) {
            sslClientContextBuilder.protocols(grpcServerConfig.getProtocols().split(","));
        }

        if (StringUtils.isNotBlank(grpcServerConfig.getCiphers())) {
            sslClientContextBuilder.ciphers(Arrays.asList(grpcServerConfig.getCiphers().split(",")));
        }
        if (grpcServerConfig.getMutualAuthEnable()) {
            // trust all certificate
            if (grpcServerConfig.getTrustCertAll()) {
                sslClientContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else {
                if (StringUtils.isBlank(grpcServerConfig.getTrustCertCollectionFile())) {
                    throw new IllegalArgumentException("enable mutual auth,trustCertCollectionFile must not be null");
                }

                InputStream clientCert = getInputStream(grpcServerConfig.getTrustCertCollectionFile(), "TrustCertCollectionFile");
                sslClientContextBuilder.trustManager(clientCert);
            }
            sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
        }
        // JDK SSL is very slower than OPENSSL。
        return GrpcSslContexts.configure(sslClientContextBuilder, SslProvider.OPENSSL);
    }

    private InputStream getInputStream(String path, String config) {
        try {
            Resource resource = resourceLoader.getResource(path);
            return resource.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(config + " load fail", e);
        }
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
