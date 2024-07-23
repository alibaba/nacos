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

package com.alibaba.nacos.common.cache.impl;

import com.alibaba.nacos.common.cache.Cache;
import com.alibaba.nacos.common.cache.builder.CacheBuilder;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimpleCacheTest {
    
    @Test
    void test() throws Exception {
        Cache cache = CacheBuilder.builder().initializeCapacity(100).build();
        IntStream.range(0, 100).forEach(item -> cache.put(item, item));
        assertEquals(100, cache.getSize());
        Object item = cache.remove(89);
        assertEquals(89, item);
        assertEquals(99, cache.getSize());
        assertNull(cache.get(89));
        assertEquals(99, cache.get(99));
        assertEquals(99, cache.get(99, () -> 99999));
        assertEquals(87, cache.get(111, () -> 87));
        cache.clear();
        assertEquals(0, cache.getSize());
    }
    
}
