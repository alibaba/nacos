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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MapUtilTest {
    
    @Test
    void testIsEmptyAndNotEmptyMap() {
        Map<Object, Object> map = null;
        assertTrue(MapUtil.isEmpty(map));
        assertFalse(MapUtil.isNotEmpty(map));
        map = new HashMap<>();
        assertTrue(MapUtil.isEmpty(map));
        assertFalse(MapUtil.isNotEmpty(map));
        map.put("a", "b");
        assertFalse(MapUtil.isEmpty(map));
        assertTrue(MapUtil.isNotEmpty(map));
    }
    
    @Test
    void testIsEmptyOrEmptyDictionary() {
        Dictionary<Object, Object> dictionary = null;
        assertTrue(MapUtil.isEmpty(dictionary));
        assertFalse(MapUtil.isNotEmpty(dictionary));
        dictionary = new Hashtable<>();
        assertTrue(MapUtil.isEmpty(dictionary));
        assertFalse(MapUtil.isNotEmpty(dictionary));
        dictionary.put("a", "b");
        assertFalse(MapUtil.isEmpty(dictionary));
        assertTrue(MapUtil.isNotEmpty(dictionary));
    }
    
    @Test
    void testPutIfValNoNull() {
        Map<Object, Object> map = new HashMap<>();
        MapUtil.putIfValNoNull(map, "key-1", null);
        assertTrue(map.isEmpty());
        MapUtil.putIfValNoNull(map, "key-1", "null");
        assertTrue(map.containsKey("key-1"));
    }
    
    @Test
    void testPutIfValNoEmptyMap() {
        Map<Object, Object> map = new HashMap<>();
        
        MapUtil.putIfValNoEmpty(map, "key-str", null);
        assertFalse(map.containsKey("key-str"));
        
        MapUtil.putIfValNoEmpty(map, "key-str", "");
        assertFalse(map.containsKey("key-str"));
        
        MapUtil.putIfValNoEmpty(map, "key-str", "1");
        assertTrue(map.containsKey("key-str"));
        
        MapUtil.putIfValNoEmpty(map, "key-list", null);
        assertFalse(map.containsKey("key-list"));
        
        MapUtil.putIfValNoEmpty(map, "key-list", Collections.emptyList());
        assertFalse(map.containsKey("key-list"));
        
        MapUtil.putIfValNoEmpty(map, "key-list", Collections.singletonList(1));
        assertTrue(map.containsKey("key-list"));
        
        MapUtil.putIfValNoEmpty(map, "key-map", null);
        assertFalse(map.containsKey("key-map"));
        
        MapUtil.putIfValNoEmpty(map, "key-map", Collections.emptyMap());
        assertFalse(map.containsKey("key-map"));
        
        Map<String, String> map1 = new HashMap<>();
        map1.put("1123", "123");
        
        MapUtil.putIfValNoEmpty(map, "key-map", map1);
        assertTrue(map.containsKey("key-map"));
        
        Dictionary dictionary = Mockito.mock(Dictionary.class);
        when(dictionary.isEmpty()).thenReturn(true);
        MapUtil.putIfValNoEmpty(map, "key-dict", dictionary);
        assertFalse(map.containsKey("key-dict"));
        when(dictionary.isEmpty()).thenReturn(false);
        MapUtil.putIfValNoEmpty(map, "key-dict", dictionary);
        assertTrue(map.containsKey("key-dict"));
    }
    
    @Test
    void testComputeIfAbsent() {
        Map<String, String> target = new HashMap<>();
        String key = "key";
        String param1 = "param1";
        String param2 = "param2";
        BiFunction<String, String, String> mappingFunction = (p1, p2) -> p1 + p2;
        
        String result = MapUtil.computeIfAbsent(target, key, mappingFunction, param1, param2);
        assertEquals("param1param2", result);
        
        // Test that the mappingFunction is only called once
        AtomicInteger counter = new AtomicInteger();
        mappingFunction = (p1, p2) -> {
            counter.incrementAndGet();
            return p1 + p2;
        };
        
        result = MapUtil.computeIfAbsent(target, key, mappingFunction, param1, param2);
        assertEquals("param1param2", result);
        assertEquals(0, counter.get());
    }
    
    @Test
    void testRemoveKey() {
        Map<String, Integer> map = new HashMap<>();
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        
        MapUtil.removeKey(map, "B", integer -> integer == 1);
        assertEquals(3, map.size());
        MapUtil.removeKey(map, "B", integer -> integer == 2);
        assertEquals(2, map.size());
    }
}
