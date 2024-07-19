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

package com.alibaba.nacos.common.cache.decorators;

import com.alibaba.nacos.common.cache.Cache;
import com.alibaba.nacos.common.cache.builder.CacheBuilder;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * lru test .
 *
 * @author zzq
 * @date 2021/7/30
 */
class LruCacheTest {
    
    @Test
    void testBasic() throws Exception {
        int capacity = 10;
        int start = 0;
        int end = 99;
        Cache cache = CacheBuilder.builder().maximumSize(capacity).lru(true).build();
        IntStream.range(start, end).forEach(item -> cache.put(item, item));
        assertEquals(capacity, cache.getSize());
        assertNull(cache.get(start));
        assertEquals(89, cache.get(89));
        assertEquals(94, cache.get(94));
        assertEquals(94, cache.get(94, () -> 100));
        Object removed = cache.remove(98);
        assertEquals(98, removed);
        assertEquals(9, cache.getSize());
        cache.clear();
        assertEquals(0, cache.getSize());
    }
    
    @Test
    void testLru() {
        int capacity = 10;
        int start = 0;
        int end = 10;
        Cache cache = CacheBuilder.builder().maximumSize(capacity).lru(true).build();
        IntStream.range(start, end).forEach(item -> cache.put(item, item));
        IntStream.range(start, 2).forEach(item -> cache.get(0));
        cache.put(100, 100);
        assertEquals(start, cache.get(0));
        assertNull(cache.get(1));
    }
}
