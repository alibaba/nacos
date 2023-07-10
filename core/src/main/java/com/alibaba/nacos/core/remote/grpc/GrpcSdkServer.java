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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.packagescan.resource.DefaultResourceLoader;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.packagescan.resource.ResourceLoader;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.TlsTypeResolve;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.InternalProtocolNegotiator;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Grpc implementation as  a rpc server.
 *
 * @author liuzunfei
 * @version $Id: BaseGrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
@Service
public class GrpcSdkServer extends BaseGrpcServer {
    
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    
    private OptionalTlsProtocolNegotiator optionalTlsProtocolNegotiator;
    
    @Override
    public int rpcPortOffset() {
        return Constants.SDK_GRPC_PORT_DEFAULT_OFFSET;
    }
    
    @Override
    public ThreadPoolExecutor getRpcExecutor() {
        return GlobalExecutor.sdkRpcExecutor;
    }
    
    @Override
    protected long getKeepAliveTime() {
        Long property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.SDK_KEEP_ALIVE_TIME_PROPERTY, Long.class);
        if (property != null) {
            return property;
        }
        return super.getKeepAliveTime();
    }
    
    @Override
    protected long getKeepAliveTimeout() {
        Long property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.SDK_KEEP_ALIVE_TIMEOUT_PROPERTY, Long.class);
        if (property != null) {
            return property;
        }
        
        return super.getKeepAliveTimeout();
    }
    
    @Override
    protected int getMaxInboundMessageSize() {
        Integer property = EnvUtil
                .getProperty(GrpcServerConstants.GrpcConfig.SDK_MAX_INBOUND_MSG_SIZE_PROPERTY, Integer.class);
        if (property != null) {
            return property;
        }
        
        int size = super.getMaxInboundMessageSize();
        
        if (Loggers.REMOTE.isWarnEnabled()) {
            Loggers.REMOTE.warn("Recommended use '{}' property instead '{}', now property value is {}",
                    GrpcServerConstants.GrpcConfig.SDK_MAX_INBOUND_MSG_SIZE_PROPERTY,
                    GrpcServerConstants.GrpcConfig.MAX_INBOUND_MSG_SIZE_PROPERTY, size);
        }
        
        return size;
    }
    
    @Override
    protected long getPermitKeepAliveTime() {
        Long property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.SDK_PERMIT_KEEP_ALIVE_TIME, Long.class);
        if (property != null) {
            return property;
        }
        return super.getPermitKeepAliveTime();
    }
    
    @Override
    protected InternalProtocolNegotiator.ProtocolNegotiator newProtocolNegotiator() {
        if (rpcServerTlsConfig.getEnableTls()) {
            optionalTlsProtocolNegotiator = new OptionalTlsProtocolNegotiator(getSslContextBuilder(),
                    rpcServerTlsConfig.getCompatibility());
            return optionalTlsProtocolNegotiator;
        }
        return null;
    }
    
    private SslContext getSslContextBuilder() {
        try {
            if (StringUtils.isBlank(rpcServerTlsConfig.getCertChainFile()) || StringUtils
                    .isBlank(rpcServerTlsConfig.getCertPrivateKey())) {
                throw new IllegalArgumentException("Server certChainFile or certPrivateKey must be not null");
            }
            InputStream certificateChainFile = getInputStream(rpcServerTlsConfig.getCertChainFile(), "certChainFile");
            InputStream privateKeyFile = getInputStream(rpcServerTlsConfig.getCertPrivateKey(), "certPrivateKey");
            SslContextBuilder sslClientContextBuilder = SslContextBuilder
                    .forServer(certificateChainFile, privateKeyFile, rpcServerTlsConfig.getCertPrivateKeyPassword());
            
            if (StringUtils.isNotBlank(rpcServerTlsConfig.getProtocols())) {
                sslClientContextBuilder.protocols(rpcServerTlsConfig.getProtocols().split(","));
            }
            
            if (StringUtils.isNotBlank(rpcServerTlsConfig.getCiphers())) {
                sslClientContextBuilder.ciphers(Arrays.asList(rpcServerTlsConfig.getCiphers().split(",")));
            }
            if (rpcServerTlsConfig.getMutualAuthEnable()) {
                // trust all certificate
                if (rpcServerTlsConfig.getTrustAll()) {
                    sslClientContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                } else {
                    if (StringUtils.isBlank(rpcServerTlsConfig.getTrustCollectionCertFile())) {
                        throw new IllegalArgumentException(
                                "enable mutual auth,trustCollectionCertFile must be not null");
                    }
                    
                    InputStream clientCert = getInputStream(rpcServerTlsConfig.getTrustCollectionCertFile(),
                            "trustCollectionCertFile");
                    sslClientContextBuilder.trustManager(clientCert);
                }
                sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
            }
            SslContextBuilder configure = GrpcSslContexts.configure(sslClientContextBuilder,
                    TlsTypeResolve.getSslProvider(rpcServerTlsConfig.getSslProvider()));
            return configure.build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }
    
    private InputStream getInputStream(String path, String config) {
        try {
            Resource resource = resourceLoader.getResource(path);
            return resource.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(config + " load fail", e);
        }
    }
    
    /**
     * reload ssl context.
     */
    public void reloadSslContext() {
        if (optionalTlsProtocolNegotiator != null) {
            try {
                optionalTlsProtocolNegotiator.setSslContext(getSslContextBuilder());
            } catch (Throwable throwable) {
                Loggers.REMOTE.info("Nacos {} Rpc server reload ssl context fail at port {} and tls config:{}",
                        this.getClass().getSimpleName(), getServicePort(),
                        JacksonUtils.toJson(super.rpcServerTlsConfig));
                throw throwable;
            }
        }
    }
}
