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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigCacheTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigCache cache = new ConfigCache();
        assertEquals("", cache.getMd5());
        assertEquals(0L, cache.getLastModifiedTs());
        assertNull(cache.getEncryptedDataKey());
    }
    
    @Test
    void testParameterizedConstructor() {
        ConfigCache cache = new ConfigCache("md5val", 12345L);
        assertEquals("md5val", cache.getMd5());
        assertEquals(12345L, cache.getLastModifiedTs());
    }
    
    @Test
    void testClear() {
        ConfigCache cache = new ConfigCache("md5val", 12345L);
        cache.setEncryptedDataKey("key");
        cache.clear();
        assertEquals("", cache.getMd5());
        assertEquals(-1L, cache.getLastModifiedTs());
        assertNull(cache.getEncryptedDataKey());
    }
    
    @Test
    void testSetters() {
        ConfigCache cache = new ConfigCache();
        cache.setMd5("newMd5");
        cache.setLastModifiedTs(999L);
        cache.setEncryptedDataKey("ek");
        assertEquals("newMd5", cache.getMd5());
        assertEquals(999L, cache.getLastModifiedTs());
        assertEquals("ek", cache.getEncryptedDataKey());
    }
}
