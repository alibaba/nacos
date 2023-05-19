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
import org.junit.Assert;
import org.junit.Test;

import java.util.stream.IntStream;

public class SimpleCacheTest {

    @Test
    public void test() throws Exception {
        Cache cache = CacheBuilder.builder().initializeCapacity(100).build();
        IntStream.range(0, 100).forEach(item -> cache.put(item, item));
        Assert.assertEquals(100, cache.getSize());
        Object item = cache.remove(89);
        Assert.assertEquals(89, item);
        Assert.assertEquals(99, cache.getSize());
        Assert.assertEquals(null, cache.get(89));
        Assert.assertEquals(99, cache.get(99));
        Assert.assertEquals(99, cache.get(99, () -> 99999));
        Assert.assertEquals(87, cache.get(111, () -> 87));
        cache.clear();
        Assert.assertEquals(0, cache.getSize());
    }
    
}
