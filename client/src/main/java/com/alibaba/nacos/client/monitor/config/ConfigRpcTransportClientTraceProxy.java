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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.client.RpcClient;

import java.util.concurrent.ScheduledExecutorService;

/**
 * {@link com.alibaba.nacos.client.config.impl.ClientWorker.ConfigRpcTransportClient} interface for dynamic proxy to
 * trace the ClientWorker.ConfigRpcTransportClient by OpenTelemetry.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public interface ConfigRpcTransportClientTraceProxy extends Closeable {
    
    // Methods for Worker level config span
    
    /**
     * Query config from server. For trace dynamic proxy.
     *
     * @param dataId       dataId
     * @param group        group
     * @param tenant       tenant
     * @param readTimeouts readTimeouts
     * @param notify       notify
     * @return ConfigResponse
     * @throws NacosException NacosException
     */
    ConfigResponse queryConfig(String dataId, String group, String tenant, long readTimeouts, boolean notify)
            throws NacosException;
    
    /**
     * Publish config to server. For trace dynamic proxy.
     *
     * @param dataId           dataId
     * @param group            group
     * @param tenant           tenant
     * @param appName          appName
     * @param tag              tag
     * @param betaIps          betaIps
     * @param content          content
     * @param encryptedDataKey encryptedDataKey
     * @param casMd5           casMd5
     * @param type             type
     * @return boolean
     * @throws NacosException NacosException
     */
    boolean publishConfig(String dataId, String group, String tenant, String appName, String tag, String betaIps,
            String content, String encryptedDataKey, String casMd5, String type) throws NacosException;
    
    /**
     * Remove config from server. For trace dynamic proxy.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @param tag    tag
     * @return boolean
     * @throws NacosException NacosException
     */
    boolean removeConfig(String dataId, String group, String tenant, String tag) throws NacosException;
    
    // Methods for Rpc level config span
    
    /**
     * Request proxy. For trace dynamic proxy.
     *
     * @param rpcClientInner rpcClientInner
     * @param request        request
     * @param timeoutMills   timeoutMills
     * @return Response
     * @throws NacosException NacosException
     */
    Response requestProxy(RpcClient rpcClientInner, Request request, long timeoutMills) throws NacosException;
    
    // Other necessary methods
    
    /**
     * Notify listen config. For trace dynamic proxy.
     */
    void notifyListenConfig();
    
    /**
     * Get tenant. For trace dynamic proxy.
     *
     * @return tenant
     */
    String getTenant();
    
    /**
     * Remove cache. For trace dynamic proxy.
     *
     * @param dataId dataId
     * @param group  group
     */
    void removeCache(String dataId, String group);
    
    /**
     * Get agent name. For trace dynamic proxy.
     *
     * @return agent name
     */
    String getName();
    
    /**
     * Get server list manager. For trace dynamic proxy.
     *
     * @return server list manager
     */
    ServerListManager getServerListManager();
    
    /**
     * Set executor. For trace dynamic proxy.
     *
     * @param executor executor
     */
    void setExecutor(ScheduledExecutorService executor);
    
    /**
     * Start agent. For trace dynamic proxy.
     *
     * @throws NacosException NacosException
     */
    void start() throws NacosException;
    
    /**
     * Check whether the server is health. For trace dynamic proxy.
     *
     * @return boolean
     */
    boolean isHealthServer();
    
}
