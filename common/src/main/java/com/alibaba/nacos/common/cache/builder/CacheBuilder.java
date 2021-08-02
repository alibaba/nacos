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

package com.alibaba.nacos.common.cache.builder;

import com.alibaba.nacos.common.cache.Cache;
import com.alibaba.nacos.common.cache.decorators.AutoExpireCache;
import com.alibaba.nacos.common.cache.decorators.LruCache;
import com.alibaba.nacos.common.cache.decorators.SynchronizedCache;
import com.alibaba.nacos.common.cache.impl.SimpleCache;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Cache builder.
 * @author zzq
 * @date 2021/7/30
 */
public class CacheBuilder<K, V> {
    
    private static final int DEFAULT_MAXIMUMSIZE = 1024;
    
    private static final int DEFAULT_INITIALIZE_CAPACITY = 1024;
    
    private static final int DEFAULT_EXPIRE_NANOS = -1;
    
    private long expireNanos = DEFAULT_EXPIRE_NANOS;
        
    private int maximumSize = DEFAULT_MAXIMUMSIZE;
    
    private int initializeCapacity = DEFAULT_INITIALIZE_CAPACITY;
    
    public static CacheBuilder builder() {
        return new CacheBuilder();
    }
    
    /**
     * Set expiration time.
     */
    public CacheBuilder expireNanos(long duration, TimeUnit unit) {
        checkExpireNanos(duration, unit);
        this.expireNanos = unit.toNanos(duration);
        return this;
    }
    
    private void checkExpireNanos(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
        if (Objects.isNull(unit)) {
            throw new IllegalArgumentException("unit cannot be null");
        }
    }
    
    /**
     * Set the maximum capacity of the cache pair.
     * @param maximumSize maximum capacity
     */
    public CacheBuilder<K, V> maximumSize(int maximumSize) {
        if (maximumSize < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
        this.maximumSize = maximumSize;
        return this;
    }
    
    /**
     * Set the initialize capacity of the cache pair.
     * @param initializeCapacity initialize capacity
     */
    public CacheBuilder<K, V> initializeCapacity(int initializeCapacity) {
        if (initializeCapacity < 0) {
            throw new IllegalArgumentException("initializeCapacity cannot be negative");
        }
        this.initializeCapacity = initializeCapacity;
        return this;
    }
    
    public Cache buildSynchronizedCache(Cache delegate) {
        return new SynchronizedCache(delegate);
    }
    
    public Cache buildAutoExpireCache(Cache delegate) {
        return new AutoExpireCache(delegate, expireNanos);
    }
    
    public Cache buidLruCache(Cache delegate) {
        return new LruCache(delegate, maximumSize);
    }
    
    public Cache buildCache() {
        return new SimpleCache(initializeCapacity);
    }
    
}
