/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;

/**
 * StringPool,aim to reduce memory allocation.
 *
 * @author liuzunfei
 * @version $Id: StringPool.java, v 0.1 2020年11月12日 3:05 PM liuzunfei Exp $
 */
public class StringPool {
    
    private static Cache<String, String> groupKeyCache = CacheBuilder.newBuilder().maximumSize(5000000)
            .expireAfterAccess(180, TimeUnit.SECONDS).build();
    
    /**
     * get singleton string value from the pool.
     *
     * @param key key string to be pooled.
     * @return value after pooled.
     */
    public static String get(String key) {
        if (key == null) {
            return key;
        }
        String value = groupKeyCache.getIfPresent(key);
        if (value == null) {
            groupKeyCache.put(key, key);
            value = groupKeyCache.getIfPresent(key);
        }
        
        return value;
    }
    
    public static long size() {
        return groupKeyCache.size();
    }
    
    public static void remove(String key) {
        groupKeyCache.invalidate(key);
    }
    
}
