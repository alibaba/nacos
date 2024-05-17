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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of CollectionUtil.
 *
 * @author <a href="mailto:jifeng.sun@outlook.com">sunjifeng</a>
 */
class CollectionUtilsTest {
    
    @Test
    void testGetList1() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(Collections.emptyList(), -1);
        });
    }
    
    @Test
    void testGetList2() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(Collections.emptyList(), 1);
        });
    }
    
    @Test
    void testGetList3() {
        assertEquals("element", CollectionUtils.get(Collections.singletonList("element"), 0));
        assertEquals("element2", CollectionUtils.get(Arrays.asList("element1", "element2"), 1));
    }
    
    @Test
    void testGetMap1() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            Map<String, String> map = new HashMap<>();
            map.put("key1", "value1");
            CollectionUtils.get(map, -1);
        });
    }
    
    @Test
    void testGetMap2() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            Map<String, String> map = new HashMap<>();
            map.put("key1", "value1");
            CollectionUtils.get(map, -1);
            CollectionUtils.get(map, 1);
        });
    }
    
    @Test
    void testGetMap3() {
        Map<String, String> map1 = new LinkedHashMap(1);
        Map<String, String> map2 = new LinkedHashMap(2);
        map1.put("key", "value");
        map2.put("key1", "value1");
        map2.put("key2", "value2");
        Iterator<Map.Entry<String, String>> iter = map1.entrySet().iterator();
        assertEquals(iter.next(), CollectionUtils.get(map1, 0));
        Iterator<Map.Entry<String, String>> iter2 = map2.entrySet().iterator();
        iter2.next();
        Map.Entry<String, String> second = iter2.next();
        assertEquals(second, CollectionUtils.get(map2, 1));
    }
    
    @Test
    void testGetArray1() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(new Object[] {}, -1);
        });
    }
    
    @Test
    void testGetArray2() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(new Object[] {}, 0);
        });
    }
    
    @Test
    void testGetArray3() {
        assertEquals("1", CollectionUtils.get(new Object[] {"1"}, 0));
        assertEquals("2", CollectionUtils.get(new Object[] {"1", "2"}, 1));
    }
    
    @Test
    void testGetArray4() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(new int[] {}, 0);
        });
    }
    
    @Test
    void testGetArray5() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(new int[] {}, -1);
        });
    }
    
    @Test
    void testGetArray6() {
        assertEquals(1, CollectionUtils.get(new int[] {1, 2}, 0));
        assertEquals(2, CollectionUtils.get(new int[] {1, 2}, 1));
    }
    
    @Test
    void testGetIterator1() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(Collections.emptyIterator(), 0);
        });
    }
    
    @Test
    void testGetIterator2() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(Collections.emptyIterator(), -1);
        });
    }
    
    @Test
    void testGetIterator3() {
        assertEquals("1", CollectionUtils.get(Collections.singleton("1").iterator(), 0));
        assertEquals("2", CollectionUtils.get(Arrays.asList("1", "2").iterator(), 1));
    }
    
    @Test
    void testGetCollection1() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(Collections.emptySet(), 0);
        });
    }
    
    @Test
    void testGetCollection2() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(Collections.emptySet(), -1);
        });
    }
    
    @Test
    void testGetCollection3() {
        assertEquals("1", CollectionUtils.get(Collections.singleton("1"), 0));
        assertEquals("2", CollectionUtils.get(CollectionUtils.set("1", "2"), 1));
    }
    
    @Test
    void testGetEnumeration1() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(asEnumeration(Collections.emptyIterator()), 0);
        });
    }
    
    @Test
    void testGetEnumeration2() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            CollectionUtils.get(asEnumeration(Collections.emptyIterator()), -1);
        });
    }
    
    @Test
    void testGetEnumeration3() {
        Vector<Object> vector = new Vector<>();
        vector.add("1");
        vector.add("2");
        
        assertEquals("1", CollectionUtils.get(vector.elements(), 0));
        assertEquals("2", CollectionUtils.get(vector.elements(), 1));
    }
    
    @Test
    void testGet1() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.get(null, 0);
        });
    }
    
    @Test
    void testGet2() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.get("string", 0);
        });
    }
    
    @Test
    void testSize() {
        // collection
        assertEquals(0, CollectionUtils.size(Collections.emptyList()));
        assertEquals(1, CollectionUtils.size(Collections.singletonList("")));
        assertEquals(10, CollectionUtils.size(IntStream.range(0, 10).boxed().collect(Collectors.toList())));
        
        // map
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        assertEquals(1, CollectionUtils.size(map));
        map.put("key2", "value2");
        assertEquals(2, CollectionUtils.size(map));
        map.put("key3", "value3");
        assertEquals(3, CollectionUtils.size(map));
        
        // array
        assertEquals(1, CollectionUtils.size(new Object[] {"1"}));
        assertEquals(2, CollectionUtils.size(new Object[] {"1", "2"}));
        assertEquals(6, CollectionUtils.size(new Object[] {"1", "2", "3", "4", "5", "6"}));
        assertEquals(1000, CollectionUtils.size(IntStream.range(0, 1000).boxed().toArray()));
        
        // primitive array
        assertEquals(1, CollectionUtils.size(new int[] {1}));
        assertEquals(2, CollectionUtils.size(new int[] {1, 2}));
        assertEquals(6, CollectionUtils.size(new int[] {1, 2, 3, 4, 5, 6}));
        assertEquals(1000, CollectionUtils.size(IntStream.range(0, 1000).toArray()));
        
        // iterator
        assertEquals(1, CollectionUtils.size(Collections.singleton("1").iterator()));
        assertEquals(2, CollectionUtils.size(Arrays.asList("1", "2").iterator()));
        
        // enumeration
        assertEquals(0, CollectionUtils.size(asEnumeration(Collections.emptyIterator())));
        assertEquals(1, CollectionUtils.size(asEnumeration(Collections.singleton("").iterator())));
    }
    
    @Test
    void testSize1() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.size(null);
        });
    }
    
    @Test
    void testSize2() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.size("string");
        });
    }
    
    @Test
    void testSizeIsEmpty() {
        // collection
        assertTrue(CollectionUtils.sizeIsEmpty(Collections.emptyList()));
        assertFalse(CollectionUtils.sizeIsEmpty(Collections.singletonList("")));
        
        // map
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        assertTrue(CollectionUtils.sizeIsEmpty(Collections.emptyMap()));
        assertFalse(CollectionUtils.sizeIsEmpty(map));
        
        // array
        assertTrue(CollectionUtils.sizeIsEmpty(new Object[] {}));
        assertFalse(CollectionUtils.sizeIsEmpty(new Object[] {"1", "2"}));
        
        // primitive array
        assertTrue(CollectionUtils.sizeIsEmpty(new int[] {}));
        assertFalse(CollectionUtils.sizeIsEmpty(new int[] {1, 2}));
        
        // iterator
        assertTrue(CollectionUtils.sizeIsEmpty(Collections.emptyIterator()));
        assertFalse(CollectionUtils.sizeIsEmpty(Arrays.asList("1", "2").iterator()));
        
        // enumeration
        assertTrue(CollectionUtils.sizeIsEmpty(asEnumeration(Collections.emptyIterator())));
        assertFalse(CollectionUtils.sizeIsEmpty(asEnumeration(Collections.singleton("").iterator())));
    }
    
    @Test
    void testSizeIsEmpty1() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.sizeIsEmpty(null);
        });
    }
    
    @Test
    void testSizeIsEmpty2() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.sizeIsEmpty("string");
        });
    }
    
    @Test
    void testContains() {
        assertTrue(CollectionUtils.contains(Collections.singletonList("target"), "target"));
        assertFalse(CollectionUtils.contains(Collections.emptyList(), "target"));
    }
    
    @Test
    void testIsEmpty() {
        assertFalse(CollectionUtils.isEmpty(Collections.singletonList("target")));
        assertTrue(CollectionUtils.isEmpty(Collections.emptyList()));
        assertTrue(CollectionUtils.isEmpty(null));
    }
    
    @Test
    void testIsNotEmpty() {
        assertTrue(CollectionUtils.isNotEmpty(Collections.singletonList("target")));
        assertFalse(CollectionUtils.isNotEmpty(Collections.emptyList()));
        assertFalse(CollectionUtils.isNotEmpty(null));
    }
    
    @Test
    void testGetOrDefault() {
        assertEquals("default", CollectionUtils.getOrDefault(Collections.emptyList(), 1, "default"));
        assertEquals("element", CollectionUtils.getOrDefault(Collections.singletonList("element"), 0, "default"));
    }
    
    @Test
    void testList() {
        assertEquals(Arrays.asList(null, null, null), CollectionUtils.list(null, null, null));
        assertEquals(Arrays.asList("", "a", "b"), CollectionUtils.list("", "a", "b"));
        assertEquals(new ArrayList(), CollectionUtils.list());
    }
    
    @Test
    void testListNullPointerException() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.list(null);
        });
    }
    
    private <T> Enumeration<T> asEnumeration(final Iterator<T> iterator) {
        if (iterator == null) {
            throw new IllegalArgumentException("iterator cannot be null ");
        }
        return new Enumeration<T>() {
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }
            
            public T nextElement() {
                return iterator.next();
            }
        };
    }
    
    @Test
    void testSet() {
        Set<Object> set = new HashSet<>();
        set.add(null);
        assertEquals(set, CollectionUtils.set(null, null, null));
        assertEquals(new LinkedHashSet(Arrays.asList("", "a", "b")), CollectionUtils.set("", "a", "b"));
        assertEquals(new HashSet(), CollectionUtils.set());
    }
    
    @Test
    void testSetNullPointerException() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.set(null);
        });
    }
    
    @Test
    void testGetOnlyElementIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6);
            CollectionUtils.getOnlyElement(list);
        });
    }
    
    @Test
    void testGetOnlyElementIllegalArgumentException2() {
        assertThrows(IllegalArgumentException.class, () -> {
            CollectionUtils.getOnlyElement(null);
        });
    }
    
    @Test
    void testGetOnlyElementNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> {
            List<Object> list = new ArrayList<>();
            CollectionUtils.getOnlyElement(list);
        });
    }
    
    @Test
    void testGetOnly() {
        List<Integer> list = Arrays.asList(1);
        int element = CollectionUtils.getOnlyElement(list);
        assertEquals(1, element);
    }
    
    @Test
    void testIsListEqualForNull() {
        assertTrue(CollectionUtils.isListEqual(null, null));
        assertFalse(CollectionUtils.isListEqual(Collections.emptyList(), null));
        assertFalse(CollectionUtils.isListEqual(null, Collections.emptyList()));
    }
    
    @Test
    void testIsListEqualForEquals() {
        List<String> list1 = Arrays.asList("1", "2", "3");
        List<String> list2 = Arrays.asList("1", "2", "3");
        assertTrue(CollectionUtils.isListEqual(list1, list1));
        assertTrue(CollectionUtils.isListEqual(list1, list2));
        assertTrue(CollectionUtils.isListEqual(list2, list1));
    }
    
    @Test
    void testIsListEqualForNotEquals() {
        List<String> list1 = Arrays.asList("1", "2", "3");
        List<String> list2 = Arrays.asList("1", "2", "3", "4");
        List<String> list3 = Arrays.asList("1", "2", "3", "5");
        assertFalse(CollectionUtils.isListEqual(list1, list2));
        assertFalse(CollectionUtils.isListEqual(list2, list3));
    }
    
    @Test
    void testIsMapEmpty() {
        assertTrue(CollectionUtils.isMapEmpty(null));
        assertTrue(CollectionUtils.isMapEmpty(Collections.emptyMap()));
    }
}
