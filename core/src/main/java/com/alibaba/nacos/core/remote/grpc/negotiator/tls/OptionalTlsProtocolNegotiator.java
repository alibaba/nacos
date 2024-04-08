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
import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfig;
import io.grpc.netty.shaded.io.grpc.netty.GrpcHttp2ConnectionHandler;
import io.grpc.netty.shaded.io.grpc.netty.InternalProtocolNegotiators;
import io.grpc.netty.shaded.io.grpc.netty.ProtocolNegotiationEvent;
import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.channel.ChannelHandler;
import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import io.grpc.netty.shaded.io.netty.handler.codec.ByteToMessageDecoder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslHandler;
import io.grpc.netty.shaded.io.netty.util.AsciiString;

import java.lang.reflect.Field;
import java.util.List;

/**
 * support the tls and plain protocol one the same port.
 *
 * @author githubcheng2978.
 */
public class OptionalTlsProtocolNegotiator implements NacosGrpcProtocolNegotiator {
    
    private static final int MAGIC_VALUE = 5;
    
    private final boolean supportPlainText;
    
    private final RpcServerTlsConfig config;
    
    private SslContext sslContext;
    
    public OptionalTlsProtocolNegotiator(SslContext sslContext, RpcServerTlsConfig config) {
        this.sslContext = sslContext;
        this.config = config;
        this.supportPlainText = config.getCompatibility();
    }
    
    void setSslContext(SslContext sslContext) {
        this.sslContext = sslContext;
    }
    
    @Override
    public AsciiString scheme() {
        return AsciiString.of("https");
    }
    
    @Override
    public ChannelHandler newHandler(GrpcHttp2ConnectionHandler grpcHttp2ConnectionHandler) {
        ChannelHandler plaintext = InternalProtocolNegotiators.serverPlaintext().newHandler(grpcHttp2ConnectionHandler);
        ChannelHandler ssl = InternalProtocolNegotiators.serverTls(sslContext).newHandler(grpcHttp2ConnectionHandler);
        return new PortUnificationServerHandler(ssl, plaintext);
    }
    
    @Override
    public void close() {
    
    }
    
    @Override
    public void reloadNegotiator() {
        if (config.getEnableTls()) {
            sslContext = DefaultTlsContextBuilder.getSslContext(config);
        }
    }
    
    private ProtocolNegotiationEvent getDefPne() {
        try {
            Field aDefault = ProtocolNegotiationEvent.class.getDeclaredField("DEFAULT");
            aDefault.setAccessible(true);
            return (ProtocolNegotiationEvent) aDefault.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public class PortUnificationServerHandler extends ByteToMessageDecoder {
        
        private final ProtocolNegotiationEvent pne;
        
        private final ChannelHandler ssl;
        
        private final ChannelHandler plaintext;
        
        public PortUnificationServerHandler(ChannelHandler ssl, ChannelHandler plaintext) {
            this.ssl = ssl;
            this.plaintext = plaintext;
            this.pne = getDefPne();
        }
        
        private boolean isSsl(ByteBuf buf) {
            return SslHandler.isEncrypted(buf);
        }
        
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() < MAGIC_VALUE) {
                return;
            }
            if (isSsl(in) || !supportPlainText) {
                ctx.pipeline().addAfter(ctx.name(), null, this.ssl);
            } else {
                ctx.pipeline().addAfter(ctx.name(), null, this.plaintext);
            }
            ctx.fireUserEventTriggered(pne);
            ctx.pipeline().remove(this);
        }
    }
    
}
