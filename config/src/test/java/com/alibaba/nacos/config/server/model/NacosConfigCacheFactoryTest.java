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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.ConfigGrayPersistInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosConfigCacheFactoryTest {
    
    @Test
    public void testCreateConfigCache() {
        NacosConfigCacheFactory nacosConfigCacheFactory = new NacosConfigCacheFactory();
        ConfigCache configCache = nacosConfigCacheFactory.createConfigCache();
        assertEquals(ConfigCache.class, configCache.getClass());
        ConfigCache configCache2 = nacosConfigCacheFactory.createConfigCache("md5", 1L);
        assertEquals(ConfigCache.class, configCache2.getClass());
        assertEquals("md5", configCache2.getMd5());
        assertEquals(1L, configCache2.getLastModifiedTs());
        ConfigCacheGray configCacheGray = nacosConfigCacheFactory.createConfigCacheGray("grayName");
        assertEquals(ConfigCacheGray.class, configCacheGray.getClass());
        assertEquals("grayName", configCacheGray.getGrayName());
        ConfigGrayPersistInfo localConfigGrayPersistInfo = new ConfigGrayPersistInfo(BetaGrayRule.TYPE_BETA,
                BetaGrayRule.VERSION, "1.1.1.1", Integer.MAX_VALUE);
        ConfigCacheGray configCacheGray2 = nacosConfigCacheFactory.createConfigCacheGray("md5", 1L, (new Gson()).toJson(localConfigGrayPersistInfo));
        assertEquals(ConfigCacheGray.class, configCacheGray2.getClass());
        assertEquals("md5", configCacheGray2.getMd5());
        assertEquals(1L, configCacheGray2.getLastModifiedTs());
    }
    
    @Test
    public void testGetConfigCacheFactoryName() {
        NacosConfigCacheFactory nacosConfigCacheFactory = new NacosConfigCacheFactory();
        assertEquals("nacos", nacosConfigCacheFactory.getConfigCacheFactoryName());
    }
}