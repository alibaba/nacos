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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Simple implementation of {@code Cache}.
 * @author zzq
 * @date 2021/7/30
 */
public class SimpleCache<K, V> implements Cache<K, V> {
    
    private Map<K, V> cache;
    
    public SimpleCache(int size) {
        cache = new HashMap<>(size);
    }
    
    @Override
    public void put(K key, V val) {
        cache.put(key, val);
    }
    
    @Override
    public V get(K key) {
        return cache.get(key);
    }
    
    @Override
    public V get(K key, Callable<? extends V> call) throws Exception {
        if (cache.containsKey(key)) {
            return cache.get(key);
        } else {
            V v2 = call.call();
            cache.put(key, v2);
            return v2;
        }
    }
    
    @Override
    public V remove(K key) {
        return cache.remove(key);
    }
    
    @Override
    public void clear() {
        cache.clear();
    }
    
    @Override
    public int getSize() {
        return cache.size();
    }
}
