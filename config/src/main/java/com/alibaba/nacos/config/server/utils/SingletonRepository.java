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
 * 避免多个相同内容的实例的工具类。比如，可以用来缓存客户端IP。
 *
 * @author Nacos
 */
public class SingletonRepository<T> {

    public SingletonRepository() {
        // 初始化大小2^16, 这个容器本身大概占用50k的内存，避免不停扩容
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
     * 必须小心使用。
     *
     * @param obj obj
     */
    public void remove(Object obj) {
        shared.remove(obj);
    }

    private final ConcurrentHashMap<T, T> shared;

    /**
     * DataId和Group的缓存。
     */
    static public class DataIdGroupIdCache {
        static public String getSingleton(String str) {
            return cache.getSingleton(str);
        }

        static SingletonRepository<String> cache = new SingletonRepository<String>();
    }
}
