/*
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
 */

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheDirUtilTest {
    
    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("user.home");
        System.clearProperty("JM.SNAPSHOT.PATH");
    }
    
    @Test
    void testInitCacheDirWithDefaultRootAndWithoutCache() {
        System.setProperty("user.home", "/home/admin");
        String actual = CacheDirUtil.initCacheDir("test", NacosClientProperties.PROTOTYPE.derive());
        assertEquals("/home/admin/nacos/naming/test", actual);
    }
    
    @Test
    void testInitCacheDirWithDefaultRootAndWithoutCache2() {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty("user.home", "/home/test");
        String actual = CacheDirUtil.initCacheDir("test", properties);
        assertEquals("/home/test/nacos/naming/test", actual);
    }
    
    @Test
    void testInitCacheDirWithDefaultRootAndWithCache() {
        System.setProperty("user.home", "/home/admin");
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR, "custom");
        String actual = CacheDirUtil.initCacheDir("test", properties);
        assertEquals("/home/admin/nacos/custom/naming/test", actual);
    }
    
    @Test
    void testInitCacheDirWithJmSnapshotPathRootAndWithoutCache() {
        System.setProperty("JM.SNAPSHOT.PATH", "/home/snapshot");
        String actual = CacheDirUtil.initCacheDir("test", NacosClientProperties.PROTOTYPE.derive());
        assertEquals("/home/snapshot/nacos/naming/test", actual);
    }
    
    @Test
    void testInitCacheDirWithJmSnapshotPathRootAndWithoutCache2() {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty("JM.SNAPSHOT.PATH", "/home/custom/snapshot");
        String actual = CacheDirUtil.initCacheDir("test", properties);
        assertEquals("/home/custom/snapshot/nacos/naming/test", actual);
    }
    
    @Test
    void testInitCacheDirWithJmSnapshotPathRootAndWithCache() {
        System.setProperty("user.home", "/home/snapshot");
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR, "custom");
        String actual = CacheDirUtil.initCacheDir("test", properties);
        assertEquals("/home/snapshot/nacos/custom/naming/test", actual);
    }
}
