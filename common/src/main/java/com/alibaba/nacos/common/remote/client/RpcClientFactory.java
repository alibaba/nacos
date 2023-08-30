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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/**
 * RpcClientFactory.to support multi client for different modules of usage.
 *
 * @author liuzunfei
 * @version $Id: RpcClientFactory.java, v 0.1 2020年07月14日 3:41 PM liuzunfei Exp $
 */
public class RpcClientFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
    private static final Map<String, RpcClient> CLIENT_MAP = new HashMap<>();
    
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    
    /**
     * Returns a snapshot view of all registered client entries.
     *
     * <p><b>Implementation Specification:</b> This method returns a read-only snapshot of the registered client
     * entries. It is not intended for modification or adding new entries. Any attempt to modify the returned set or add
     * new entries may lead to unexpected behavior and data integrity issues.
     *
     * @return A snapshot view of registered client entries.
     */
    public static Set<Map.Entry<String, RpcClient>> getUnmodifiableClientEntries() {
        LOCK.readLock().lock();
        try {
            return Collections.unmodifiableSet(CLIENT_MAP.entrySet());
        } finally {
            LOCK.readLock().unlock();
        }
    }
    
    /**
     * shut down client.
     *
     * @param clientName client name.
     */
    public static void destroyClient(String clientName) throws NacosException {
        LOCK.writeLock().lock();
        try {
            RpcClient rpcClient = CLIENT_MAP.remove(clientName);
            if (rpcClient != null) {
                rpcClient.shutdown();
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }
    
    /**
     * Shutdown clients that satisfy the provided predicate.
     *
     * @param predicate The predicate function.
     * @return A list of successfully destroyed client keys.
     */
    public static List<String> destroyClient(Predicate<Map.Entry<String, RpcClient>> predicate) throws NacosException {
        LOCK.writeLock().lock();
        List<String> successfullyDestroyedKeys = new ArrayList<>();
        try {
            for (Map.Entry<String, RpcClient> entry : CLIENT_MAP.entrySet()) {
                if (predicate.test(entry)) {
                    destroyClient(entry.getKey());
                    successfullyDestroyedKeys.add(entry.getKey());
                }
            }
            return successfullyDestroyedKeys;
        } finally {
            LOCK.writeLock().unlock();
        }
    }
    
    public static RpcClient getClient(String clientName) {
        LOCK.readLock().lock();
        try {
            return CLIENT_MAP.get(clientName);
        } finally {
            LOCK.readLock().unlock();
        }
    }
    
    /**
     * create a rpc client.
     *
     * @param clientName     client name.
     * @param connectionType client type.
     * @return rpc client.
     */
    public static RpcClient createClient(String clientName, ConnectionType connectionType, Map<String, String> labels) {
        return createClient(clientName, connectionType, null, null, labels);
    }
    
    public static RpcClient createClient(String clientName, ConnectionType connectionType, Map<String, String> labels,
            RpcClientTlsConfig tlsConfig) {
        return createClient(clientName, connectionType, null, null, labels, tlsConfig);
        
    }
    
    public static RpcClient createClient(String clientName, ConnectionType connectionType, Integer threadPoolCoreSize,
            Integer threadPoolMaxSize, Map<String, String> labels) {
        return createClient(clientName, connectionType, threadPoolCoreSize, threadPoolMaxSize, labels, null);
    }
    
    /**
     * create a rpc client.
     *
     * @param clientName         client name.
     * @param connectionType     client type.
     * @param threadPoolCoreSize grpc thread pool core size
     * @param threadPoolMaxSize  grpc thread pool max size
     * @param tlsConfig          tlsconfig
     * @return rpc client.
     */
    public static RpcClient createClient(String clientName, ConnectionType connectionType, Integer threadPoolCoreSize,
            Integer threadPoolMaxSize, Map<String, String> labels, RpcClientTlsConfig tlsConfig) {
        
        if (!ConnectionType.GRPC.equals(connectionType)) {
            throw new UnsupportedOperationException("unsupported connection type :" + connectionType.getType());
        }
        LOCK.writeLock().lock();
        try {
            return CLIENT_MAP.computeIfAbsent(clientName, clientNameInner -> {
                LOGGER.info("[RpcClientFactory] create a new rpc client of " + clientName);
                try {
                    return new GrpcSdkClient(clientNameInner, threadPoolCoreSize, threadPoolMaxSize, labels, tlsConfig);
                } catch (Throwable throwable) {
                    LOGGER.error("Error to init GrpcSdkClient for client name :" + clientName, throwable);
                    throw throwable;
                }
                
            });
        } finally {
            LOCK.writeLock().unlock();
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
        return createClusterClient(clientName, connectionType, null, null, labels);
    }
    
    public static RpcClient createClusterClient(String clientName, ConnectionType connectionType,
            Map<String, String> labels, RpcClientTlsConfig tlsConfig) {
        return createClusterClient(clientName, connectionType, null, null, labels, tlsConfig);
    }
    
    /**
     * create a rpc client.
     *
     * @param clientName         client name.
     * @param connectionType     client type.
     * @param threadPoolCoreSize grpc thread pool core size
     * @param threadPoolMaxSize  grpc thread pool max size
     * @return rpc client.
     */
    public static RpcClient createClusterClient(String clientName, ConnectionType connectionType,
            Integer threadPoolCoreSize, Integer threadPoolMaxSize, Map<String, String> labels) {
        return createClusterClient(clientName, connectionType, threadPoolCoreSize, threadPoolMaxSize, labels, null);
    }
    
    /**
     * createClusterClient.
     *
     * @param clientName         client name.
     * @param connectionType     connectionType.
     * @param threadPoolCoreSize coreSize.
     * @param threadPoolMaxSize  threadPoolSize.
     * @param labels             tables.
     * @param tlsConfig          tlsConfig.
     * @return
     */
    
    public static RpcClient createClusterClient(String clientName, ConnectionType connectionType,
            Integer threadPoolCoreSize, Integer threadPoolMaxSize, Map<String, String> labels,
            RpcClientTlsConfig tlsConfig) {
        if (!ConnectionType.GRPC.equals(connectionType)) {
            throw new UnsupportedOperationException("unsupported connection type :" + connectionType.getType());
        }
        LOCK.writeLock().lock();
        try {
            return CLIENT_MAP.computeIfAbsent(clientName,
                    clientNameInner -> new GrpcClusterClient(clientNameInner, threadPoolCoreSize, threadPoolMaxSize,
                            labels, tlsConfig));
        } finally {
            LOCK.writeLock().unlock();
        }
    }
}
