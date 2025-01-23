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

package com.alibaba.nacos.maintainer.client.address;

import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.exception.NacosException;
import com.alibaba.nacos.maintainer.client.lifecycle.Closeable;
import com.alibaba.nacos.maintainer.client.remote.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;

import java.util.List;

/**
 * Server list provider.
 * 
 * @author Nacos
 */
public interface ServerListProvider extends Closeable {
    
    /**
     * Init.
     * @param properties nacos client properties
     * @param nacosRestTemplate nacos rest template
     * @throws NacosException nacos exception
     */
    void init(final NacosClientProperties properties, final NacosRestTemplate nacosRestTemplate) throws NacosException;
    
    /**
     * Get server list.
     * @return server list
     */
    List<String> getServerList();
    
    /**
     * Get context path.
     * @return context path
     */
    default String getContextPath() {
        return ParamUtil.getDefaultContextPath();
    }
    
    /**
     * Get order.
     * @return order
     */
    int getOrder();
    
    /**
     * Match.
     * @param properties nacos client properties
     * @return match
     */
    boolean match(final NacosClientProperties properties);
    
    /**
     * check the server list is fixed or not.
     * @return true if the server list is fixed
     */
    default boolean isFixed() {
        return false;
    }
    
    /**
     * Get address source.
     * @return address source
     */
    default String getAddressSource() {
        return "";
    }
}
