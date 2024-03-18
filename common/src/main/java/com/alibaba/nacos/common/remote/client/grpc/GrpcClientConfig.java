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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.common.remote.TlsConfig;
import com.alibaba.nacos.common.remote.client.RpcClientConfig;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;

/**
 * GrpcCleint config. Use to collect and init Grpc client configuration.
 *
 * @author karsonto
 */
public interface GrpcClientConfig extends RpcClientConfig {
    
    /**
     * get threadPoolCoreSize.
     *
     * @return threadPoolCoreSize.
     */
    int threadPoolCoreSize();
    
    /**
     * get threadPoolMaxSize.
     *
     * @return threadPoolMaxSize.
     */
    int threadPoolMaxSize();
    
    /**
     * get thread pool keep alive time.
     *
     * @return threadPoolKeepAlive.
     */
    long threadPoolKeepAlive();
    
    /**
     * get server check time out.
     *
     * @return serverCheckTimeOut.
     */
    long serverCheckTimeOut();
    
    /**
     * get thread pool queue size.
     *
     * @return threadPoolQueueSize.
     */
    int threadPoolQueueSize();
    
    /**
     * get maxInboundMessage size.
     *
     * @return maxInboundMessageSize.
     */
    int maxInboundMessageSize();
    
    /**
     * get channelKeepAlive time.
     *
     * @return channelKeepAlive.
     */
    int channelKeepAlive();
    
    /**
     * get channelKeepAliveTimeout.
     *
     * @return channelKeepAliveTimeout.
     */
    long channelKeepAliveTimeout();
    
    /**
     * getTlsConfig.
     *
     * @return TlsConfig.
     */
    TlsConfig tlsConfig();
    
    /**
     * Set TlsConfig.
     *
     * @param tlsConfig tlsConfig of client.
     */
    void setTlsConfig(RpcClientTlsConfig tlsConfig);
    
    /**
     * get timeout of connection setup(TimeUnit.MILLISECONDS).
     *
     * @return timeout of connection setup
     */
    long capabilityNegotiationTimeout();
    
}
