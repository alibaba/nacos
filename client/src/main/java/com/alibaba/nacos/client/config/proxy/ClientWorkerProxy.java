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

package com.alibaba.nacos.client.config.proxy;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.CacheData;
import com.alibaba.nacos.common.lifecycle.Closeable;

import java.util.List;

/**
 * proxy interface for ClientWorker.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public interface ClientWorkerProxy extends Closeable {
    
    /**
     * Add listener to config.
     *
     * @param dataId           dataId
     * @param group            group
     * @param content          content
     * @param encryptedDataKey encryptedDataKey
     * @param listeners        listener
     * @throws NacosException NacosException
     */
    void addTenantListenersWithContent(String dataId, String group, String content, String encryptedDataKey,
            List<? extends Listener> listeners) throws NacosException;
    
    /**
     * Add listener to config.
     *
     * @param dataId    dataId
     * @param group     group
     * @param listeners listener
     * @throws NacosException NacosException
     */
    void addTenantListeners(String dataId, String group, List<? extends Listener> listeners) throws NacosException;
    
    /**
     * Remove listener from config.
     *
     * @param dataId   dataId
     * @param group    group
     * @param listener listener
     */
    void removeTenantListener(String dataId, String group, Listener listener);
    
    /**
     * Remove all listeners from config.
     *
     * @param dataId      dataId
     * @param group       group
     * @param tenant      tenant
     * @param readTimeout readTimeout
     * @param notify      notify
     * @return ConfigResponse
     * @throws NacosException NacosException
     */
    ConfigResponse getServerConfig(String dataId, String group, String tenant, long readTimeout, boolean notify)
            throws NacosException;
    
    /**
     * Remove all listeners from config.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @param tag    tag
     * @return boolean
     * @throws NacosException NacosException
     */
    boolean removeConfig(String dataId, String group, String tenant, String tag) throws NacosException;
    
    /**
     * Remove all listeners from config.
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
     * Add cache data if absent.
     *
     * @param dataId data id if data
     * @param group  group of data
     * @return cache data
     */
    CacheData addCacheDataIfAbsent(String dataId, String group);
    
    /**
     * Add cache data if absent.
     *
     * @param dataId data id if data
     * @param group  group of data
     * @param tenant tenant of data
     * @return cache data
     * @throws NacosException NacosException
     */
    CacheData addCacheDataIfAbsent(String dataId, String group, String tenant) throws NacosException;
    
    /**
     * Check whether the server is health.
     *
     * @return boolean
     */
    boolean isHealthServer();
    
    // Other necessary methods
    
    /**
     * Get agent tenant.
     *
     * @return tenant
     */
    String getAgentTenant();
    
    /**
     * Get agent name.
     *
     * @return name
     */
    String getAgentName();
    
}
