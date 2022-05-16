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
import com.alibaba.nacos.common.cache.builder.CacheItemProperties;

import java.util.HashMap;
import java.util.concurrent.Callable;

/**
 * A wrapper that automatically expires the cache.
 * @author zzq
 * @date 2021/7/30
 */
public class AutoExpireCache<K, V> implements Cache<K, V> {
    
    private long expireNanos;
    
    private Cache<K, V> delegate;
    
    private HashMap<K, CacheItemProperties> keyProp = new HashMap<>();
    
    public AutoExpireCache(Cache<K, V> delegate, long expireNanos) {
        this.expireNanos = expireNanos;
        this.delegate = delegate;
    }
    
    @Override
    public void put(K key, V val) {
        keyProp.put(key, cacheItemProperties());
        this.delegate.put(key, val);
    }
    
    @Override
    public V get(K key) {
        if (keyProp.get(key) != null && isExpire(keyProp.get(key))) {
            this.keyProp.remove(key);
            this.delegate.remove(key);
            return null;
        }
        return this.delegate.get(key);
    }

    @Override
    public V get(K key, Callable<? extends V> call) throws Exception {
        V cachedValue = this.get(key);
        if (null == cachedValue) {
            V v2 = call.call();
            this.put(key, v2);
            return v2;

        }
        return cachedValue;

    }
    
    @Override
    public V remove(K key) {
        keyProp.remove(key);
        return this.delegate.remove(key);
    }
    
    @Override
    public void clear() {
        keyProp.clear();
        this.delegate.clear();
    }
    
    @Override
    public int getSize() {
        return this.delegate.getSize();
    }
    
    private boolean isExpire(CacheItemProperties itemProperties) {
        if (itemProperties == null) {
            return true;
        }
        return expireNanos != -1 && (System.nanoTime() - itemProperties.getExpireNanos() > expireNanos);
    }
    
    private CacheItemProperties cacheItemProperties() {
        CacheItemProperties cacheItemProperties = new CacheItemProperties();
        cacheItemProperties.setExpireNanos(System.nanoTime());
        return cacheItemProperties;
    }
}
