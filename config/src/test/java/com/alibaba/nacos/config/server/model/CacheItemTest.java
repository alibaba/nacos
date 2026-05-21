/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CacheItemTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testConstructorAndSimpleAccessors() {
        CacheItem cacheItem = new CacheItem("groupKey", "encryptedKey");
        cacheItem.setType("properties");
        
        assertEquals("groupKey", cacheItem.getGroupKey());
        assertEquals("encryptedKey", cacheItem.getConfigCache().getEncryptedDataKey());
        assertEquals("properties", cacheItem.getType());
        assertNotNull(cacheItem.getRwLock());
    }
    
    @Test
    void testSortConfigGrayWhenEmpty() {
        CacheItem cacheItem = new CacheItem("groupKey");
        cacheItem.sortConfigGray();
        
        assertNull(cacheItem.getSortConfigGrays());
    }
    
    @Test
    void testSortConfigGrayByPriority() {
        CacheItem cacheItem = new CacheItem("groupKey");
        cacheItem.initConfigGrayIfEmpty("low");
        cacheItem.initConfigGrayIfEmpty("high");
        cacheItem.getConfigCacheGray().get("low")
            .resetGrayRule("{\"type\":\"tag\",\"version\":\"1.0.0\","
                + "\"expr\":\"low\",\"priority\":1}");
        cacheItem.getConfigCacheGray().get("high")
            .resetGrayRule("{\"type\":\"tag\",\"version\":\"1.0.0\","
                + "\"expr\":\"high\",\"priority\":2}");
        
        cacheItem.sortConfigGray();
        
        assertEquals("high", cacheItem.getSortConfigGrays().get(0).getGrayName());
        assertEquals("low", cacheItem.getSortConfigGrays().get(1).getGrayName());
    }
}
