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

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.api.common.Constants;

/**
 * Rpc client system property config.
 */
public class RpcClientSystemConfig {
    
    private static final String GRPC_CHANNEL_MAX_INBOUND_MESSAGE_SIZE = "nacos.remote.client.grpc.channel.maxinbound.message.size";
    
    private static final long DEFAULT_GRPC_CHANNEL_MAX_INBOUND_MESSAGE_SIZE = 10 * 1024 * 1024L;
    
    private static final String GRPC_CHANNEL_KEEP_ALIVE_MILLIS = "nacos.remote.grpc.channel.keep.alive.millis";
    
    private static final long DEFAULT_CHANNEL_KEEP_ALIVE_MILLIS = 6 * 60 * 1000L;
    
    private static final String NACOS_SERVER_GRPC_PORT_OFFSET_KEY = "nacos.server.grpc.port.offset";
    
    private static final String NACOS_SERVER_PORT = "nacos.server.port";
    
    private static final int DEFAULT_NACOS_SERVER_PORT = 8848;
    
    private static final String GRPC_EXECUTOR_KEEP_ALIVE_MILLIS = "nacos.remote.grpc.executor.keep.alive.millis";
    
    private static final long DEFAULT_GRPC_EXECUTOR_KEEP_ALIVE_MILLIS = 10 * 1000L;
    
    private static final String GRPC_REQUEST_TIMEOUT_MILLIS = "nacos.remote.grpc.request.timeout.millis";
    
    private static final long DEFAULT_GRPC_REQUEST_TIMEOUT_MILLIS = 3 * 1000L;
    
    private static final String RPC_REQUEST_TIMEOUT_MILLIS = "nacos.remote.rpc.request.timeout.millis";
    
    private static final long DEFAULT_RPC_REQUEST_TIMEOUT_MILLIS = 3 * 1000L;
    
    private static final String RPC_REQUEST_RETRY_TIMES = "nacos.remote.rpc.request.retry.times";
    
    private static final int DEFAULT_RPC_REQUEST_RETRY_TIMES = 3;
    
    private static final String RPC_KEEP_ALIVE_TIME_MILLIS = "nacos.remote.rpc.server.keep.alive.millis";
    
    private static final long DEFAULT_RPC_KEEP_ALIVE_TIME_MILLIS = 5 * 1000L;
    
    public static int getGrpcChannelInboundMessageSize() {
        return Integer.parseInt(System.getProperty(GRPC_CHANNEL_MAX_INBOUND_MESSAGE_SIZE,
                String.valueOf(DEFAULT_GRPC_CHANNEL_MAX_INBOUND_MESSAGE_SIZE)));
    }
    
    public static int getGrpcChannelKeepAliveMillis() {
        return Integer.parseInt(System.getProperty(GRPC_CHANNEL_KEEP_ALIVE_MILLIS,
                String.valueOf(DEFAULT_CHANNEL_KEEP_ALIVE_MILLIS)));
    }
    
    public static int getServerRpcPortOffset() {
        return Integer.parseInt(System.getProperty(NACOS_SERVER_GRPC_PORT_OFFSET_KEY,
                String.valueOf(Constants.CLUSTER_GRPC_PORT_DEFAULT_OFFSET)));
    }
    
    public static int getSdkRpcPortOffset() {
        return Integer.parseInt(System.getProperty(NACOS_SERVER_GRPC_PORT_OFFSET_KEY,
                String.valueOf(Constants.SDK_GRPC_PORT_DEFAULT_OFFSET)));
    }
    
    public static int getNacosServerPort() {
        return Integer.parseInt(System.getProperty(NACOS_SERVER_PORT,
                String.valueOf(DEFAULT_NACOS_SERVER_PORT)));
    }
    
    public static long getGrpcExecutorKeepAliveMillis() {
        return Integer.parseInt(System.getProperty(GRPC_EXECUTOR_KEEP_ALIVE_MILLIS,
                String.valueOf(DEFAULT_GRPC_EXECUTOR_KEEP_ALIVE_MILLIS)));
    }
    
    public static long getGrpcRequestTimeoutMillis() {
        return Integer.parseInt(System.getProperty(GRPC_REQUEST_TIMEOUT_MILLIS,
                String.valueOf(DEFAULT_GRPC_REQUEST_TIMEOUT_MILLIS)));
    }
    
    public static long getRpcRequestTimeoutMillis() {
        return Integer.parseInt(System.getProperty(RPC_REQUEST_TIMEOUT_MILLIS,
                String.valueOf(DEFAULT_RPC_REQUEST_TIMEOUT_MILLIS)));
    }
    
    public static int getRpcRequestRetryTimes() {
        return Integer.parseInt(System.getProperty(RPC_REQUEST_RETRY_TIMES,
                String.valueOf(DEFAULT_RPC_REQUEST_RETRY_TIMES)));
    }
    
    public static int getRpcKeepAliveMillis() {
        return Integer.parseInt(System.getProperty(RPC_KEEP_ALIVE_TIME_MILLIS,
                String.valueOf(DEFAULT_RPC_KEEP_ALIVE_TIME_MILLIS)));
    }
}
