/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * Nacos naming maintainer service.
 *
 * @author Nacos
 */
public class NamingMaintainerFactory {
    
    /**
     * create Naming maintainer service.
     *
     * @param serverList server list
     * @return naming maintainer service
     * @throws NacosException nacos exception
     */
    public static NamingMaintainerService createNamingMaintainerService(String serverList) throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverList);
        return new NacosNamingMaintainerServiceImpl(properties);
    }
    
    /**
     * create Naming maintainer service.
     *
     * @param properties properties
     * @return naming maintainer service
     * @throws NacosException nacos exception
     */
    public static NamingMaintainerService createNamingMaintainerService(Properties properties) throws NacosException {
        if (properties == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "properties is null");
        }
        return new NacosNamingMaintainerServiceImpl(properties);
    }
}