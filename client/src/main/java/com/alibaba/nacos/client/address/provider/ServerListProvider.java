/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.provider;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;

import java.util.List;

/**
 * Server List Provider.
 *
 * @author misakacoder
 */
public interface ServerListProvider {
    
    /**
     * start init the server list provider.
     *
     * @param properties init params
     * @param namespace  namespace
     * @param moduleType module type
     * @throws NacosException Exception
     */
    void startup(NacosClientProperties properties, String namespace, ModuleType moduleType) throws NacosException;
    
    /**
     * Checks if the server list provider is valid.
     *
     * @return true if the server list provider is valid, otherwise returns false
     */
    boolean isValid();
    
    /**
     * Get server list from server list provider.
     *
     * @return server list
     * @throws NacosException Exception
     */
    List<String> getServerList() throws NacosException;
    
    /**
     * Get server list provider name.
     *
     * @return name
     */
    String getName();
    
    /**
     * Get server list provider order.
     *
     * @return order
     */
    int getOrder();
    
    /**
     * Get the URL of the address server (Only for AddressServerListProvider).
     *
     * @return address server url.
     */
    default String getAddressServerUrl() {
        return "";
    }
    
    ;
    
    /**
     * Check if refresh operation is supported.
     *
     * @return true if refresh operation is supported, otherwise returns false
     */
    boolean supportRefresh();
    
    /**
     * Shuts down the current service or resource.
     *
     * @throws NacosException Exception.
     */
    default void shutdown() throws NacosException {
    
    }
}
