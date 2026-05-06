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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * Nacos config maintainer service.
 *
 * @author Nacos
 */
public class ConfigMaintainerFactory {
    
    /**
     * create config maintainer service.
     *
     * @param serverList server list
     * @return config maintainer service
     * @throws NacosException nacos exception
     */
    public static ConfigMaintainerService createConfigMaintainerService(String serverList) throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverList);
        return new NacosConfigMaintainerServiceImpl(properties);
    }
    
    /**
     * create config maintainer service.
     *
     * @param properties properties
     * @return config maintainer service
     * @throws NacosException nacos exception
     */
    public static ConfigMaintainerService createConfigMaintainerService(Properties properties) throws NacosException {
        if (properties == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Properties cannot be null");
        }
        return new NacosConfigMaintainerServiceImpl(properties);
    }
}