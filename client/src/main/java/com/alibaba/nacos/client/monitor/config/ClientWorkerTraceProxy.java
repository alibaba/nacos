/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.monitor.config;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.CacheData;
import com.alibaba.nacos.client.config.impl.ConfigTransportClient;

import java.util.List;

/**
 * {@link com.alibaba.nacos.client.config.impl.ClientWorker} interface for dynamic proxy to trace the ClientWorker by
 * OpenTelemetry.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public interface ClientWorkerTraceProxy {
    
    // Methods for Service level config span
    void addTenantListenersWithContent(String dataId, String group, String content, String encryptedDataKey,
            List<? extends Listener> listeners) throws NacosException;
    
    void addTenantListeners(String dataId, String group, List<? extends Listener> listeners) throws NacosException;
    
    void removeTenantListener(String dataId, String group, Listener listener);
    
    ConfigResponse getServerConfig(String dataId, String group, String tenant, long readTimeout, boolean notify)
            throws NacosException;
    
    boolean removeConfig(String dataId, String group, String tenant, String tag) throws NacosException;
    
    boolean publishConfig(String dataId, String group, String tenant, String appName, String tag, String betaIps,
            String content, String encryptedDataKey, String casMd5, String type) throws NacosException;
    
    boolean isHealthServer();
    
    // Methods for Worker level config span
    CacheData addCacheDataIfAbsent(String dataId, String group);
    
    CacheData addCacheDataIfAbsent(String dataId, String group, String tenant) throws NacosException;
    
    CacheData getCache(String dataId, String group, String tenant);
    
    // Other necessary methods
    ConfigTransportClient getAgent();
    
    String getAgentName();
    
    void shutdown() throws NacosException;
}
