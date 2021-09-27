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

package com.alibaba.nacos.common.cache;

import java.util.concurrent.Callable;

/**
 * Cache method collection definition.
 * @author zzq
 * @date 2021/7/30
 */
public interface Cache<K, V> {

    /**
     * Cache a pair of key value. If the key value already exists, the value will be overwritten.
     * @param key cache key
     * @param val cache value
     */
    void put(K key, V val);
    
    /**
     * Take the corresponding value from the cache according to the cache key.
     * @param key cache key
     * @return cache value
     */
    V get(K key);
    
    /**
     * Get the value in the cache according to the primary key, and put it into the cache after processing by the function.
     * @param key cache key
     * @param call a function, the return value of the function will be updated to the cache
     * @return cache value
     * @throws Exception callable function interface throw exception
     */
    V get(K key, Callable<? extends V> call) throws Exception;
    
    /**
     * Take the corresponding value from the cache according to the cache key, and remove this record from the cache.
     * @param key cache key
     * @return cache value
     */
    V remove(K key);
    
    /**
     * Clear the entire cache.
     */
    void clear();
    
    /**
     * Returns the number of key-value pairs in the cache.
     * @return  number of key-value pairs
     */
    int getSize();
}
