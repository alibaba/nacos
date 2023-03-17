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

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.shaded.io.netty.channel.Channel;

import java.util.concurrent.TimeUnit;

/**
 * Grpc server side constants.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/5 15:48
 */
final class GrpcServerConstants {
    
    static final Attributes.Key<String> ATTR_TRANS_KEY_CONN_ID = Attributes.Key.create("conn_id");
    
    static final Attributes.Key<String> ATTR_TRANS_KEY_REMOTE_IP = Attributes.Key.create("remote_ip");
    
    static final Attributes.Key<Integer> ATTR_TRANS_KEY_REMOTE_PORT = Attributes.Key.create("remote_port");
    
    static final Attributes.Key<Integer> ATTR_TRANS_KEY_LOCAL_PORT = Attributes.Key.create("local_port");
    
    static final Context.Key<String> CONTEXT_KEY_CONN_ID = Context.key("conn_id");
    
    static final Context.Key<String> CONTEXT_KEY_CONN_REMOTE_IP = Context.key("remote_ip");
    
    static final Context.Key<Integer> CONTEXT_KEY_CONN_REMOTE_PORT = Context.key("remote_port");
    
    static final Context.Key<Integer> CONTEXT_KEY_CONN_LOCAL_PORT = Context.key("local_port");
    
    static final Context.Key<Channel> CONTEXT_KEY_CHANNEL = Context.key("ctx_channel");
    
    static final String REQUEST_BI_STREAM_SERVICE_NAME = "BiRequestStream";
    
    static final String REQUEST_BI_STREAM_METHOD_NAME = "requestBiStream";
    
    static final String REQUEST_SERVICE_NAME = "Request";
    
    static final String REQUEST_METHOD_NAME = "request";
    
    static class GrpcConfig {
        
        private static final String NACOS_REMOTE_SERVER_GRPC_PREFIX = "nacos.remote.server.grpc.";
        
        private static final String NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX = NACOS_REMOTE_SERVER_GRPC_PREFIX + "sdk.";
        
        private static final String NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX =
                NACOS_REMOTE_SERVER_GRPC_PREFIX + "cluster.";
        
        @Deprecated
        static final String MAX_INBOUND_MSG_SIZE_PROPERTY = NACOS_REMOTE_SERVER_GRPC_PREFIX + "maxinbound.message.size";
        
        static final String SDK_MAX_INBOUND_MSG_SIZE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "max-inbound-message-size";
        
        static final String SDK_KEEP_ALIVE_TIME_PROPERTY = NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "keep-alive-time";
        
        static final String SDK_KEEP_ALIVE_TIMEOUT_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "keep-alive-timeout";
        
        static final String SDK_PERMIT_KEEP_ALIVE_TIME = NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "permit-keep-alive-time";
        
        static final String CLUSTER_MAX_INBOUND_MSG_SIZE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "max-inbound-message-size";
        
        static final String CLUSTER_KEEP_ALIVE_TIME_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "keep-alive-time";
        
        static final String CLUSTER_KEEP_ALIVE_TIMEOUT_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "keep-alive-timeout";
        
        static final String CLUSTER_PERMIT_KEEP_ALIVE_TIME =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "permit-keep-alive-time";
        
        static final int DEFAULT_GRPC_MAX_INBOUND_MSG_SIZE = 10 * 1024 * 1024;
        
        static final long DEFAULT_GRPC_KEEP_ALIVE_TIME = TimeUnit.NANOSECONDS.toMillis(GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIME_NANOS);
        
        static final long DEFAULT_GRPC_KEEP_ALIVE_TIMEOUT = TimeUnit.NANOSECONDS.toMillis(GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIMEOUT_NANOS);
        
        static final long DEFAULT_GRPC_PERMIT_KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(5L);
    }
}
