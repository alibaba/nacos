/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientConfigFactory;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nacos AI GRPC protocol client.
 *
 * @author xiweng.yy
 */
public class AiGrpcClient implements Closeable {
    
    private final String namespaceId;
    
    private final String uuid;
    
    private final RpcClient rpcClient;
    
    private final AbstractServerListManager serverListManager;
    
    private SecurityProxy securityProxy;
    
    public AiGrpcClient(NacosClientProperties properties) {
        this.namespaceId = properties.getProperty(PropertyKeyConst.NAMESPACE);
        this.uuid = UUID.randomUUID().toString();
        this.rpcClient = buildRpcClient(properties);
        this.serverListManager = new NamingServerListManager(properties, namespaceId);
    }
    
    private RpcClient buildRpcClient(NacosClientProperties properties) {
        Map<String, String> labels = new HashMap<>(3);
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_AI);
        labels.put(Constants.APPNAME, AppNameUtils.getAppName());
        GrpcClientConfig grpcClientConfig = RpcClientConfigFactory.getInstance()
                .createGrpcClientConfig(properties.asProperties(), labels);
        return RpcClientFactory.createClient(uuid, ConnectionType.GRPC, grpcClientConfig);
    }
    
    /**
     * Start the grpc client.
     *
     * @throws NacosException nacos exception
     */
    public void start() throws NacosException {
        this.serverListManager.start();
        rpcClient.serverListFactory(this.serverListManager);
        rpcClient.start();
        this.securityProxy = new SecurityProxy(this.serverListManager,
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
    }
    
    @Override
    public void shutdown() throws NacosException {
        rpcClient.shutdown();
        serverListManager.shutdown();
        if (null != securityProxy) {
            serverListManager.shutdown();
        }
    }
}
