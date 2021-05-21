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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RpcClientFactory.to support muti client for diffrent modules of usage.
 *
 * @author liuzunfei
 * @version $Id: RpcClientFactory.java, v 0.1 2020年07月14日 3:41 PM liuzunfei Exp $
 */
public class RpcClientFactory {
    
    static Map<String, RpcClient> clientMap = new HashMap<String, RpcClient>();
    
    /**
     * get all client.
     *
     * @return client collection.
     */
    public static Set<Map.Entry<String, RpcClient>> getAllClientEntries() {
        Set<Map.Entry<String, RpcClient>> entries = clientMap.entrySet();
        return entries;
    }
    
    /**
     * shut down client.
     *
     * @param clientName client name.
     */
    public static void destroyClient(String clientName) throws NacosException {
        RpcClient rpcClient = clientMap.remove(clientName);
        if (rpcClient != null) {
            rpcClient.shutdown();
        }
    }
    
    public static RpcClient getClient(String clientName) {
        
        return clientMap.get(clientName);
    }
    
    /**
     * create a rpc client.
     *
     * @param clientName     client name.
     * @param connectionType client type.
     * @return rpc client.
     */
    public static RpcClient createClient(String clientName, ConnectionType connectionType, Map<String, String> labels) {
        String clientNameInner = clientName;
        synchronized (clientMap) {
            if (clientMap.get(clientNameInner) == null) {
                RpcClient moduleClient = null;
                if (ConnectionType.GRPC.equals(connectionType)) {
                    moduleClient = new GrpcSdkClient(clientNameInner);
                    
                }
                if (moduleClient == null) {
                    throw new UnsupportedOperationException("unsupported connection type :" + connectionType.getType());
                }
                moduleClient.labels(labels);
                clientMap.put(clientNameInner, moduleClient);
                return moduleClient;
            }
            return clientMap.get(clientNameInner);
        }
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
        String clientNameInner = clientName;
        synchronized (clientMap) {
            if (clientMap.get(clientNameInner) == null) {
                RpcClient moduleClient = null;
                if (ConnectionType.GRPC.equals(connectionType)) {
                    moduleClient = new GrpcClusterClient(clientNameInner);
                    
                }
                if (moduleClient == null) {
                    throw new UnsupportedOperationException("unsupported connection type :" + connectionType.getType());
                }
                moduleClient.labels(labels);
                clientMap.put(clientNameInner, moduleClient);
                return moduleClient;
            }
            return clientMap.get(clientNameInner);
        }
    }
    
}
