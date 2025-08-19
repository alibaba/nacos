/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * A2a maintainer factory.
 *
 * @author KiteSoar
 */
public class A2aMaintainerFactory {
    
    /**
     * Create a2a maintainer service.
     *
     * @param properties properties
     * @return a2a maintainer service
     * @throws NacosException nacos exception
     */
    public static A2aMaintainerService createA2aMaintainerService(Properties properties) throws NacosException {
        if (properties == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "properties is null");
        }

        return new NacosA2aMaintainerServiceImpl(properties);
    }
}
