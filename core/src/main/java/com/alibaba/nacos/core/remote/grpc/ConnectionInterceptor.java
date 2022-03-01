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

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * ConnectionInterceptor set connection.
 *
 * @author Weizhan▪Yun
 * @date 2022/12/27 20:44
 */
public class ConnectionInterceptor implements ServerInterceptor {
    
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
        
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
