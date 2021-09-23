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
import org.junit.Assert;
import org.junit.Test;

import java.util.stream.IntStream;

/**
 * lru test .
 * @author zzq
 * @date 2021/7/30
 */
public class LruCacheTest {

    @Test
    public void testBasic() throws Exception {
        int capacity = 10;
        int start = 0;
        int end = 99;
        Cache cache = CacheBuilder.builder().maximumSize(capacity).lru(true).build();
        IntStream.range(start, end).forEach(item -> cache.put(item, item));
        Assert.assertEquals(capacity, cache.getSize());
        Assert.assertNull(cache.get(start));
        Assert.assertEquals(89, cache.get(89));
        Assert.assertEquals(94, cache.get(94));
        Assert.assertEquals(94, cache.get(94, () -> 100));
        Object removed = cache.remove(98);
        Assert.assertEquals(98, removed);
        Assert.assertEquals(9, cache.getSize());
        cache.clear();
        Assert.assertEquals(0, cache.getSize());
    }

    @Test
    public void testLru() {
        int capacity = 10;
        int start = 0;
        int end = 10;
        Cache cache = CacheBuilder.builder().maximumSize(capacity).lru(true).build();
        IntStream.range(start, end).forEach(item -> cache.put(item, item));
        IntStream.range(start, 2).forEach(item -> cache.get(0));
        cache.put(100, 100);
        Assert.assertEquals(start, cache.get(0));
        Assert.assertNull(cache.get(1));
    }
}
