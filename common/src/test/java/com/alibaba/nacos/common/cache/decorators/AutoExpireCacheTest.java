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

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * auto expire cache test.
 * @author zzq
 * @date 2021/8/1
 */
public class AutoExpireCacheTest {
    
    @Test
    public void testBasic() throws Exception {
        Cache cache = CacheBuilder.builder().expireNanos(10, TimeUnit.MINUTES).build();
        IntStream.range(0, 100).forEach(item -> cache.put(item, item));
        Assert.assertEquals(100, cache.getSize());
        Assert.assertEquals(99, cache.get(99));
        Assert.assertEquals(99, cache.get(99, () -> 100));
        Object removed = cache.remove(99);
        Assert.assertEquals(99, removed);
        Assert.assertEquals(99, cache.getSize());
        Assert.assertEquals(100, cache.get(99, () -> 100));
        cache.clear();
        Assert.assertEquals(0, cache.getSize());
    }

    @Test
    public void testExpire() throws Exception {
        Cache cache = CacheBuilder.builder().expireNanos(1, TimeUnit.SECONDS).build();
        cache.put("a",  "a");
        TimeUnit.SECONDS.sleep(2);
        Assert.assertNull(cache.get("a"));
    }

    @Test
    public void testGetCache() {
        Cache cache = CacheBuilder.builder().expireNanos(1, TimeUnit.MINUTES).build();
        cache.put("test", "test");
        Assert.assertNotNull(cache.get("test"));
        Assert.assertNull(cache.get("test2"));
    }
}
