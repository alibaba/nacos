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

import java.util.concurrent.TimeUnit;

/**
 * Grpc server side constants.
 *
 * @author Weizhan▪Yun
 * @date 2022/12/27 20:25
 */
public class GrpcServerConstants {
    
    public static final Attributes.Key<String> ATTR_TRANS_KEY_CONN_ID = Attributes.Key.create("conn_id");
    
    public static final Attributes.Key<String> ATTR_TRANS_KEY_REMOTE_IP = Attributes.Key.create("remote_ip");
    
    public static final Attributes.Key<Integer> ATTR_TRANS_KEY_REMOTE_PORT = Attributes.Key.create("remote_port");
    
    public static final Attributes.Key<Integer> ATTR_TRANS_KEY_LOCAL_PORT = Attributes.Key.create("local_port");
    
    public static final Context.Key<String> CONTEXT_KEY_CONN_ID = Context.key("conn_id");
    
    public static final Context.Key<String> CONTEXT_KEY_CONN_REMOTE_IP = Context.key("remote_ip");
    
    public static final Context.Key<Integer> CONTEXT_KEY_CONN_REMOTE_PORT = Context.key("remote_port");
    
    public static final Context.Key<Integer> CONTEXT_KEY_CONN_LOCAL_PORT = Context.key("local_port");
    
    public static final String REQUEST_BI_STREAM_SERVICE_NAME = "BiRequestStream";
    
    public static final String REQUEST_BI_STREAM_METHOD_NAME = "requestBiStream";
    
    public static final String REQUEST_SERVICE_NAME = "Request";
    
    public static final String REQUEST_METHOD_NAME = "request";
    
    static class GrpcConfig {
        
        private static final String NACOS_REMOTE_SERVER_GRPC_PREFIX = "nacos.remote.server.grpc.";
        
        private static final String NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX = NACOS_REMOTE_SERVER_GRPC_PREFIX + "sdk.";
        
        private static final String NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX =
                NACOS_REMOTE_SERVER_GRPC_PREFIX + "cluster.";
        
        @Deprecated
        public static final String MAX_INBOUND_MSG_SIZE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_PREFIX + "maxinbound.message.size";
        
        public static final String SDK_MAX_INBOUND_MSG_SIZE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "max-inbound-message-size";
        
        public static final String SDK_KEEP_ALIVE_TIME_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "keep-alive-time";
        
        public static final String SDK_KEEP_ALIVE_TIMEOUT_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "keep-alive-timeout";
        
        public static final String SDK_PERMIT_KEEP_ALIVE_TIME =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "permit-keep-alive-time";
        
        public static final String SDK_MAX_CONNECTION_IDLE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_SDK_PREFIX + "max-connection-idle";
        
        public static final String CLUSTER_MAX_INBOUND_MSG_SIZE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "max-inbound-message-size";
        
        public static final String CLUSTER_KEEP_ALIVE_TIME_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "keep-alive-time";
        
        public static final String CLUSTER_KEEP_ALIVE_TIMEOUT_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "keep-alive-timeout";
        
        public static final String CLUSTER_MAX_CONNECTION_IDLE_PROPERTY =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "max-connection-idle";
        
        public static final String CLUSTER_PERMIT_KEEP_ALIVE_TIME =
                NACOS_REMOTE_SERVER_GRPC_CLUSTER_PREFIX + "permit-keep-alive-time";
        
        public static final int DEFAULT_GRPC_MAX_INBOUND_MSG_SIZE = 10 * 1024 * 1024;
        
        public static final long DEFAULT_GRPC_KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(6L);
        
        public static final long DEFAULT_GRPC_KEEP_ALIVE_TIMEOUT = TimeUnit.MINUTES.toMillis(18L);
        
        public static final long DEFAULT_GRPC_MAX_CONNECTION_IDLE = TimeUnit.SECONDS.toMillis(30L);
        
        public static final long DEFAULT_GRPC_PERMIT_KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(10L);
    }
}
