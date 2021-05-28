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

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.common.NotThreadSafe;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Map utils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MapUtil {
    
    /**
     * Null-safe check if the specified Dictionary is empty.
     *
     * <p>Null returns true.
     *
     * @param map the collection to check, may be null
     * @return true if empty or null
     */
    public static boolean isEmpty(Map map) {
        return (map == null || map.isEmpty());
    }
    
    /**
     * Null-safe check if the specified Dictionary is empty.
     *
     * <p>Null returns true.
     *
     * @param coll the collection to check, may be null
     * @return true if empty or null
     */
    public static boolean isEmpty(Dictionary coll) {
        return (coll == null || coll.isEmpty());
    }
    
    /**
     * Null-safe check if the specified Dictionary is not empty.
     *
     * <p>Null returns false.
     *
     * @param map the collection to check, may be null
     * @return true if non-null and non-empty
     */
    public static boolean isNotEmpty(Map map) {
        return !isEmpty(map);
    }
    
    /**
     * Null-safe check if the specified Dictionary is not empty.
     *
     * <p>Null returns false.
     *
     * @param coll the collection to check, may be null
     * @return true if non-null and non-empty
     */
    public static boolean isNotEmpty(Dictionary coll) {
        return !isEmpty(coll);
    }
    
    /**
     * Put into map if value is not null.
     *
     * @param target target map
     * @param key    key
     * @param value  value
     */
    public static void putIfValNoNull(Map target, Object key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value != null) {
            target.put(key, value);
        }
    }
    
    /**
     * Put into map if value is not empty.
     *
     * @param target target map
     * @param key    key
     * @param value  value
     */
    public static void putIfValNoEmpty(Map target, Object key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value instanceof String) {
            if (StringUtils.isNotEmpty((String) value)) {
                target.put(key, value);
            }
            return;
        }
        if (value instanceof Collection) {
            if (CollectionUtils.isNotEmpty((Collection) value)) {
                target.put(key, value);
            }
            return;
        }
        if (value instanceof Map) {
            if (isNotEmpty((Map) value)) {
                target.put(key, value);
            }
            return;
        }
        if (value instanceof Dictionary) {
            if (isNotEmpty((Dictionary) value)) {
                target.put(key, value);
            }
        }
    }
    
    /**
     * ComputeIfAbsent lazy load.
     *
     * @param target          target Map data.
     * @param key             map key.
     * @param mappingFunction function which is need to be executed.
     * @param param1          function's parameter value1.
     * @param param2          function's parameter value1.
     * @return
     */
    @NotThreadSafe
    public static <K, C, V, T> V computeIfAbsent(Map<K, V> target, K key, BiFunction<C, T, V> mappingFunction, C param1,
            T param2) {
        
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        Objects.requireNonNull(param1, "param1");
        Objects.requireNonNull(param2, "param2");
        
        V val = target.get(key);
        if (val == null) {
            V ret = mappingFunction.apply(param1, param2);
            target.put(key, ret);
            return ret;
        }
        return val;
    }
    
    /**
     * remove value, Thread safety depends on whether the Map is a thread-safe Map.
     *
     * @param map map
     * @param key key
     * @param removeJudge judge this key can be remove
     * @param <K> key type
     * @param <V> value type
     * @return value
     */
    public static <K, V> V removeKey(Map<K, V> map, K key, Predicate<V> removeJudge) {
        return map.computeIfPresent(key, (k, v) -> removeJudge.test(v) ? null : v);
    }
}
