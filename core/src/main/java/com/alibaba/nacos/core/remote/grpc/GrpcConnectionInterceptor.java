/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.internal.ServerStream;
import io.grpc.internal.ServerStreamHelper;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelHelper;
import io.grpc.netty.shaded.io.netty.channel.Channel;

/**
 * GrpcConnectionInterceptor set connection.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/5 16:05
 */
public class GrpcConnectionInterceptor implements ServerInterceptor {
    
    @Override
    public <T, S> ServerCall.Listener<T> interceptCall(ServerCall<T, S> call, Metadata headers,
            ServerCallHandler<T, S> next) {
        Context ctx = Context.current().withValue(GrpcServerConstants.CONTEXT_KEY_CONN_ID,
                        call.getAttributes().get(GrpcServerConstants.ATTR_TRANS_KEY_CONN_ID))
                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_REMOTE_IP,
                        call.getAttributes().get(GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_IP))
                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_REMOTE_PORT,
                        call.getAttributes().get(GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_PORT))
                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_LOCAL_PORT,
                        call.getAttributes().get(GrpcServerConstants.ATTR_TRANS_KEY_LOCAL_PORT));
        if (GrpcServerConstants.REQUEST_BI_STREAM_SERVICE_NAME.equals(call.getMethodDescriptor().getServiceName())) {
            Channel internalChannel = getInternalChannel(call);
            ctx = ctx.withValue(GrpcServerConstants.CONTEXT_KEY_CHANNEL, internalChannel);
        }
        
        return Contexts.interceptCall(ctx, call, headers, next);
    }
    
    private Channel getInternalChannel(ServerCall serverCall) {
        ServerStream serverStream = ServerStreamHelper.getServerStream(serverCall);
        return NettyChannelHelper.getChannel(serverStream);
    }
}
