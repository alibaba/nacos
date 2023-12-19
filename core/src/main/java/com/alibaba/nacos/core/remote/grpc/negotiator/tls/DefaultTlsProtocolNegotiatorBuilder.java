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
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

/**
 * Default optional tls protocol negotiator builder.
 *
 * @author xiweng.yy
 */
public class DefaultTlsProtocolNegotiatorBuilder implements ProtocolNegotiatorBuilder {
    
    public static final String TYPE_DEFAULT_TLS = "DEFAULT_TLS";
    
    @Override
    public NacosGrpcProtocolNegotiator build() {
        RpcServerTlsConfig rpcServerTlsConfig = RpcServerTlsConfig.getInstance();
        if (rpcServerTlsConfig.getEnableTls()) {
            SslContext sslContext = DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
            return new OptionalTlsProtocolNegotiator(sslContext, rpcServerTlsConfig.getCompatibility());
        }
        return null;
    }
    
    @Override
    public String type() {
        return TYPE_DEFAULT_TLS;
    }
}
