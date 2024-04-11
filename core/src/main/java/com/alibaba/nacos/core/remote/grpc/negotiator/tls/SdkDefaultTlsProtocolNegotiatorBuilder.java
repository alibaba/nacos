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

import com.alibaba.nacos.core.remote.grpc.negotiator.NacosGrpcProtocolNegotiator;
import com.alibaba.nacos.core.remote.grpc.negotiator.ProtocolNegotiatorBuilder;
import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfigFactory;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

import java.util.Properties;

/**
 * The {@code SdkDefaultTlsProtocolNegotiatorBuilder} class is an implementation of the
 * {@link ProtocolNegotiatorBuilder} interface for constructing a ProtocolNegotiator specifically for SDK-to-Server
 * communication with optional TLS encryption.
 *
 * <p>It defines the type as {@code SDK_DEFAULT_TLS} and supports communication types for SDKs.
 * </p>
 *
 * <p>The {@code build()} method constructs and returns a {@link NacosGrpcProtocolNegotiator} instance based on the
 * configuration provided by the {@link RpcServerTlsConfig} class. If TLS encryption is enabled, it creates an
 * {@link OptionalTlsProtocolNegotiator} with the corresponding SSL context and configuration; otherwise, it returns
 * null.
 * </p>
 *
 * <p>The {@code type()} method returns the unique identifier {@code SDK_TYPE_DEFAULT_TLS} for this negotiator builder.
 * </p>
 *
 * <p>Example Usage:
 * <pre>{@code
 * ProtocolNegotiatorBuilder builder = new SdkDefaultTlsProtocolNegotiatorBuilder();
 * NacosGrpcProtocolNegotiator negotiator = builder.build();
 * }</pre>
 * </p>
 *
 * @author xiweng.yy
 * @date 2023/12/23
 * @see ProtocolNegotiatorBuilder
 * @see NacosGrpcProtocolNegotiator
 * @see RpcServerTlsConfig
 * @see OptionalTlsProtocolNegotiator
 */
public class SdkDefaultTlsProtocolNegotiatorBuilder implements ProtocolNegotiatorBuilder {
    
    /**
     * The unique identifier for this negotiator builder.
     */
    public static final String TYPE_DEFAULT_TLS = "DEFAULT_TLS";
    
    /**
     * Constructs and returns a ProtocolNegotiator for SDK-to-Server communication with optional TLS encryption.
     *
     * @return ProtocolNegotiator, or null if TLS is not enabled.
     */
    @Override
    public NacosGrpcProtocolNegotiator build() {
        Properties properties = EnvUtil.getProperties();
        RpcServerTlsConfig config = RpcServerTlsConfigFactory.getInstance().createSdkConfig(properties);
        if (config.getEnableTls()) {
            SslContext sslContext = DefaultTlsContextBuilder.getSslContext(config);
            return new OptionalTlsProtocolNegotiator(sslContext, config);
        }
        return null;
    }
    
    /**
     * Returns the unique identifier {@code SDK_TYPE_DEFAULT_TLS} for this negotiator builder.
     *
     * @return The type identifier.
     */
    @Override
    public String type() {
        return TYPE_DEFAULT_TLS;
    }
}
