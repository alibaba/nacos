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

/**
 * A utility class that avoids multiple instances of the same content. For example, it can be used to cache client IP.
 *
 * @author Nacos
 */
public class SingletonRepository<T> {
    
    public SingletonRepository() {
        // Initializing size 2^16, the container itself use about 50K of memory, avoiding constant expansion
        shared = new ConcurrentHashMap<T, T>(1 << 16);
    }
    
    public T getSingleton(T obj) {
        T previous = shared.putIfAbsent(obj, obj);
        return (null == previous) ? obj : previous;
    }
    
    public int size() {
        return shared.size();
    }
    
    /**
     * Be careful use.
     */
    public void remove(Object obj) {
        shared.remove(obj);
    }
    
    private final ConcurrentHashMap<T, T> shared;
    
    /**
     * Cache of DataId and Group.
     */
    public static class DataIdGroupIdCache {
        
        public static String getSingleton(String str) {
            return cache.getSingleton(str);
        }
        
        static SingletonRepository<String> cache = new SingletonRepository<String>();
    }
}
