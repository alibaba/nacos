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

package com.alibaba.nacos.config.server.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A simple Cache with TTL, not cleared for expired entry.
 *
 * @param <E> the cache type
 * @author fengHan, jiuRen
 */
public class SimpleCache<E> {
    
    final ConcurrentMap<String, CacheEntry<E>> cache = new ConcurrentHashMap<String, CacheEntry<E>>();
    
    private static class CacheEntry<E> {
        
        final long expireTime;
        
        final E value;
        
        public CacheEntry(E value, long expire) {
            this.expireTime = expire;
            this.value = value;
        }
    }
    
    /**
     * Put data.
     */
    public void put(String key, E e, long ttlMs) {
        if (key == null || e == null) {
            return;
        }
        CacheEntry<E> entry = new CacheEntry<E>(e, System.currentTimeMillis() + ttlMs);
        cache.put(key, entry);
    }
    
    /**
     * Get data.
     */
    public E get(String key) {
        CacheEntry<E> entry = cache.get(key);
        if (entry != null && entry.expireTime > System.currentTimeMillis()) {
            return entry.value;
        }
        return null;
    }
}
