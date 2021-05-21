/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Config Service Interface.
 *
 * @author Nacos
 */
public interface ConfigService {
    
    /**
     * Get config.
     *
     * @param dataId    dataId
     * @param group     group
     * @param timeoutMs read timeout
     * @return config value
     * @throws NacosException NacosException
     */
    String getConfig(String dataId, String group, long timeoutMs) throws NacosException;
    
    /**
     * Get config and register Listener.
     *
     * <p>If you want to pull it yourself when the program starts to get the configuration for the first time, and the
     * registered Listener is used for future configuration updates, you can keep the original code unchanged, just add
     * the system parameter: enableRemoteSyncConfig = "true" ( But there is network overhead); therefore we recommend
     * that you use this interface directly
     *
     * @param dataId    dataId
     * @param group     group
     * @param timeoutMs read timeout
     * @param listener  {@link Listener}
     * @return config value
     * @throws NacosException NacosException
     */
    String getConfigAndSignListener(String dataId, String group, long timeoutMs, Listener listener)
            throws NacosException;
    
    /**
     * Add a listener to the configuration, after the server modified the configuration, the client will use the
     * incoming listener callback. Recommended asynchronous processing, the application can implement the getExecutor
     * method in the ManagerListener, provide a thread pool of execution. If not provided, use the main thread callback, May
     * block other configurations or be blocked by other configurations.
     *
     * @param dataId   dataId
     * @param group    group
     * @param listener listener
     * @throws NacosException NacosException
     */
    void addListener(String dataId, String group, Listener listener) throws NacosException;
    
    /**
     * Publish config.
     *
     * @param dataId  dataId
     * @param group   group
     * @param content content
     * @return Whether publish
     * @throws NacosException NacosException
     */
    boolean publishConfig(String dataId, String group, String content) throws NacosException;
    
    
    /**
     * Publish config.
     *
     * @param dataId  dataId
     * @param group   group
     * @param content content
     * @param type    config type {@link ConfigType}
     * @return Whether publish
     * @throws NacosException NacosException
     */
    boolean publishConfig(String dataId, String group, String content, String type) throws NacosException;
    
    /**
     * Cas Publish config.
     *
     * @param dataId  dataId
     * @param group   group
     * @param content content
     * @param casMd5  casMd5 prev content's md5 to cas.
     * @return Whether publish
     * @throws NacosException NacosException
     */
    boolean publishConfigCas(String dataId, String group, String content, String casMd5) throws NacosException;
    
    /**
     * Cas Publish config.
     *
     * @param dataId  dataId
     * @param group   group
     * @param content content
     * @param casMd5  casMd5 prev content's md5 to cas.
     * @param type    config type {@link ConfigType}
     * @return Whether publish
     * @throws NacosException NacosException
     */
    boolean publishConfigCas(String dataId, String group, String content, String casMd5, String type)
            throws NacosException;
    
    /**
     * Remove config.
     *
     * @param dataId dataId
     * @param group  group
     * @return whether remove
     * @throws NacosException NacosException
     */
    boolean removeConfig(String dataId, String group) throws NacosException;
    
    /**
     * Remove listener.
     *
     * @param dataId   dataId
     * @param group    group
     * @param listener listener
     */
    void removeListener(String dataId, String group, Listener listener);
    
    /**
     * Get server status.
     *
     * @return whether health
     */
    String getServerStatus();
    
    /**
     * Shutdown the resource service.
     *
     * @throws NacosException exception.
     */
    void shutDown() throws NacosException;
}
