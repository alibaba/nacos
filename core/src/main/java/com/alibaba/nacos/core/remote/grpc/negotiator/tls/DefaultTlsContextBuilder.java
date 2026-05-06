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

package com.alibaba.nacos.core.remote.grpc.negotiator.tls;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.packagescan.resource.DefaultResourceLoader;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.packagescan.resource.ResourceLoader;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.TlsTypeResolve;
import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfig;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Ssl context builder.
 *
 * @author xiweng.yy
 */
public class DefaultTlsContextBuilder {
    
    private static final ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();
    
    static SslContext getSslContext(RpcServerTlsConfig rpcServerTlsConfig) {
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
            Loggers.REMOTE.info("Nacos Rpc server reload ssl context fail tls config:{}",
                    JacksonUtils.toJson(rpcServerTlsConfig));
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    private static InputStream getInputStream(String path, String config) {
        try {
            Resource resource = RESOURCE_LOADER.getResource(path);
            return resource.getInputStream();
        } catch (IOException e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, config + " load fail", e);
        }
    }
}
