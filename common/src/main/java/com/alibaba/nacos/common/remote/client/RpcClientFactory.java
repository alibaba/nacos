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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClusterClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcSdkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RpcClientFactory.to support muti client for diffrent modules of usage.
 *
 * @author liuzunfei
 * @version $Id: RpcClientFactory.java, v 0.1 2020年07月14日 3:41 PM liuzunfei Exp $
 */
public class RpcClientFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
    private static final Map<String, RpcClient> CLIENT_MAP = new ConcurrentHashMap<>();
    
    /**
     * get all client.
     *
     * @return client collection.
     */
    public static Set<Map.Entry<String, RpcClient>> getAllClientEntries() {
        return CLIENT_MAP.entrySet();
    }
    
    /**
     * shut down client.
     *
     * @param clientName client name.
     */
    public static void destroyClient(String clientName) throws NacosException {
        RpcClient rpcClient = CLIENT_MAP.remove(clientName);
        if (rpcClient != null) {
            rpcClient.shutdown();
        }
    }
    
    public static RpcClient getClient(String clientName) {
        return CLIENT_MAP.get(clientName);
    }
    
    /**
     * create a rpc client.
     *
     * @param clientName     client name.
     * @param connectionType client type.
     * @return rpc client.
     */
    public static RpcClient createClient(String clientName, ConnectionType connectionType, Map<String, String> labels) {
        return CLIENT_MAP.compute(clientName, (clientNameInner, client) -> {
            if (client == null) {
                LOGGER.info("[RpcClientFactory] create a new rpc client of " + clientName);
                if (ConnectionType.GRPC.equals(connectionType)) {
                    client = new GrpcSdkClient(clientNameInner);
                }
                if (client == null) {
                    throw new UnsupportedOperationException("unsupported connection type :" + connectionType.getType());
                }
                client.labels(labels);
            }
            return client;
        });
    }
    
    /**
     * create a rpc client.
     *
     * @param clientName     client name.
     * @param connectionType client type.
     * @return rpc client.
     */
    public static RpcClient createClusterClient(String clientName, ConnectionType connectionType,
            Map<String, String> labels) {
        return CLIENT_MAP.compute(clientName, (clientNameInner, client) -> {
            if (client == null) {
                if (ConnectionType.GRPC.equals(connectionType)) {
                    client = new GrpcClusterClient(clientNameInner);
                }
                if (client == null) {
                    throw new UnsupportedOperationException("unsupported connection type :" + connectionType.getType());
                }
                client.labels(labels);
                return client;
            }
            return client;
        });
    }
    
}
