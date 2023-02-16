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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A wrapper that lru cache.
 * @author zzq
 * @date 2021/7/30
 */
public class LruCache<K, V> implements Cache<K, V> {
    
    private final Cache<K, V> delegate;
    
    private Map<K, V> keyMap;
    
    private K eldestKey;
    
    public LruCache(Cache<K, V> delegate, int size) {
        this.delegate = delegate;
        setSize(size);
    }
    
    @Override
    public int getSize() {
        return delegate.getSize();
    }
    
    public void setSize(final int size) {
        keyMap = new LinkedHashMap<K, V>(size, .75F, true) {
            private static final long serialVersionUID = 4267176411845948333L;
            
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean tooBig = size() > size;
                if (tooBig) {
                    eldestKey = eldest.getKey();
                }
                return tooBig;
            }
          
        };
    }
    
    @Override
    public void put(K key, V val) {
        delegate.put(key, val);
        cycleKeyList(key);
    }
    
    @Override
    public V get(K key) {
        keyMap.get(key);
        return delegate.get(key);
    }
    
    @Override
    public V get(K key, Callable<? extends V> call) throws Exception {
        return this.delegate.get(key, call);
    }
    
    @Override
    public V remove(K key) {
        return delegate.remove(key);
    }
    
    @Override
    public void clear() {
        delegate.clear();
        keyMap.clear();
    }
    
    private void cycleKeyList(K key) {
        keyMap.put(key, null);
        if (eldestKey != null) {
            delegate.remove(eldestKey);
            eldestKey = null;
        }
    }
    
}
