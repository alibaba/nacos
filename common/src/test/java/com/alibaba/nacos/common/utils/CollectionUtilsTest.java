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

import org.junit.Assert;
import org.junit.Test;

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

/**
 * Unit test of CollectionUtil.
 *
 * @author <a href="mailto:jifeng.sun@outlook.com">sunjifeng</a>
 */
public class CollectionUtilsTest {
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetList1() {
        CollectionUtils.get(Collections.emptyList(), -1);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetList2() {
        CollectionUtils.get(Collections.emptyList(), 1);
    }
    
    @Test
    public void testGetList3() {
        Assert.assertEquals("element", CollectionUtils.get(Collections.singletonList("element"), 0));
        Assert.assertEquals("element2", CollectionUtils.get(Arrays.asList("element1", "element2"), 1));
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetMap1() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        CollectionUtils.get(map, -1);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetMap2() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        CollectionUtils.get(map, -1);
        CollectionUtils.get(map, 1);
    }
    
    @Test
    public void testGetMap3() {
        Map<String, String> map1 = new LinkedHashMap(1);
        Map<String, String> map2 = new LinkedHashMap(2);
        map1.put("key", "value");
        map2.put("key1", "value1");
        map2.put("key2", "value2");
        Iterator<Map.Entry<String, String>> iter = map1.entrySet().iterator();
        Assert.assertEquals(iter.next(), CollectionUtils.get(map1, 0));
        Iterator<Map.Entry<String, String>> iter2 = map2.entrySet().iterator();
        iter2.next();
        Map.Entry<String, String> second = iter2.next();
        Assert.assertEquals(second, CollectionUtils.get(map2, 1));
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetArray1() {
        CollectionUtils.get(new Object[] {}, -1);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetArray2() {
        CollectionUtils.get(new Object[] {}, 0);
    }
    
    @Test
    public void testGetArray3() {
        Assert.assertEquals("1", CollectionUtils.get(new Object[] {"1"}, 0));
        Assert.assertEquals("2", CollectionUtils.get(new Object[] {"1", "2"}, 1));
    }
    
    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetArray4() {
        CollectionUtils.get(new int[] {}, 0);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetArray5() {
        CollectionUtils.get(new int[] {}, -1);
    }
    
    @Test
    public void testGetArray6() {
        Assert.assertEquals(1, CollectionUtils.get(new int[] {1, 2}, 0));
        Assert.assertEquals(2, CollectionUtils.get(new int[] {1, 2}, 1));
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetIterator1() {
        CollectionUtils.get(Collections.emptyIterator(), 0);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetIterator2() {
        CollectionUtils.get(Collections.emptyIterator(), -1);
    }
    
    @Test
    public void testGetIterator3() {
        Assert.assertEquals("1", CollectionUtils.get(Collections.singleton("1").iterator(), 0));
        Assert.assertEquals("2", CollectionUtils.get(Arrays.asList("1", "2").iterator(), 1));
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetCollection1() {
        CollectionUtils.get(Collections.emptySet(), 0);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetCollection2() {
        CollectionUtils.get(Collections.emptySet(), -1);
    }
    
    @Test
    public void testGetCollection3() {
        Assert.assertEquals("1", CollectionUtils.get(Collections.singleton("1"), 0));
        Assert.assertEquals("2", CollectionUtils.get(CollectionUtils.set("1", "2"), 1));
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetEnumeration1() {
        CollectionUtils.get(asEnumeration(Collections.emptyIterator()), 0);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetEnumeration2() {
        CollectionUtils.get(asEnumeration(Collections.emptyIterator()), -1);
    }
    
    @Test
    public void testGetEnumeration3() {
        Vector<Object> vector = new Vector<>();
        vector.add("1");
        vector.add("2");
        
        Assert.assertEquals("1", CollectionUtils.get(vector.elements(), 0));
        Assert.assertEquals("2", CollectionUtils.get(vector.elements(), 1));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGet1() {
        CollectionUtils.get(null, 0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGet2() {
        CollectionUtils.get("string", 0);
    }
    
    @Test
    public void testSize() {
        // collection
        Assert.assertEquals(0, CollectionUtils.size(Collections.emptyList()));
        Assert.assertEquals(1, CollectionUtils.size(Collections.singletonList("")));
        Assert.assertEquals(10, CollectionUtils.size(IntStream.range(0, 10).boxed().collect(Collectors.toList())));
        
        // map
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        Assert.assertEquals(1, CollectionUtils.size(map));
        map.put("key2", "value2");
        Assert.assertEquals(2, CollectionUtils.size(map));
        map.put("key3", "value3");
        Assert.assertEquals(3, CollectionUtils.size(map));
        
        // array
        Assert.assertEquals(1, CollectionUtils.size(new Object[] {"1"}));
        Assert.assertEquals(2, CollectionUtils.size(new Object[] {"1", "2"}));
        Assert.assertEquals(6, CollectionUtils.size(new Object[] {"1", "2", "3", "4", "5", "6"}));
        Assert.assertEquals(1000, CollectionUtils.size(IntStream.range(0, 1000).boxed().toArray()));
        
        // primitive array
        Assert.assertEquals(1, CollectionUtils.size(new int[] {1}));
        Assert.assertEquals(2, CollectionUtils.size(new int[] {1, 2}));
        Assert.assertEquals(6, CollectionUtils.size(new int[] {1, 2, 3, 4, 5, 6}));
        Assert.assertEquals(1000, CollectionUtils.size(IntStream.range(0, 1000).toArray()));
        
        // iterator
        Assert.assertEquals(1, CollectionUtils.size(Collections.singleton("1").iterator()));
        Assert.assertEquals(2, CollectionUtils.size(Arrays.asList("1", "2").iterator()));
        
        // enumeration
        Assert.assertEquals(0, CollectionUtils.size(asEnumeration(Collections.emptyIterator())));
        Assert.assertEquals(1, CollectionUtils.size(asEnumeration(Collections.singleton("").iterator())));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSize1() {
        CollectionUtils.size(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSize2() {
        CollectionUtils.size("string");
    }
    
    @Test
    public void testSizeIsEmpty() {
        // collection
        Assert.assertTrue(CollectionUtils.sizeIsEmpty(Collections.emptyList()));
        Assert.assertFalse(CollectionUtils.sizeIsEmpty(Collections.singletonList("")));
        
        // map
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        Assert.assertTrue(CollectionUtils.sizeIsEmpty(Collections.emptyMap()));
        Assert.assertFalse(CollectionUtils.sizeIsEmpty(map));
        
        // array
        Assert.assertTrue(CollectionUtils.sizeIsEmpty(new Object[] {}));
        Assert.assertFalse(CollectionUtils.sizeIsEmpty(new Object[] {"1", "2"}));
        
        // primitive array
        Assert.assertTrue(CollectionUtils.sizeIsEmpty(new int[] {}));
        Assert.assertFalse(CollectionUtils.sizeIsEmpty(new int[] {1, 2}));
        
        // iterator
        Assert.assertTrue(CollectionUtils.sizeIsEmpty(Collections.emptyIterator()));
        Assert.assertFalse(CollectionUtils.sizeIsEmpty(Arrays.asList("1", "2").iterator()));
        
        // enumeration
        Assert.assertTrue(CollectionUtils.sizeIsEmpty(asEnumeration(Collections.emptyIterator())));
        Assert.assertFalse(CollectionUtils.sizeIsEmpty(asEnumeration(Collections.singleton("").iterator())));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSizeIsEmpty1() {
        CollectionUtils.sizeIsEmpty(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSizeIsEmpty2() {
        CollectionUtils.sizeIsEmpty("string");
    }
    
    @Test
    public void testContains() {
        Assert.assertTrue(CollectionUtils.contains(Collections.singletonList("target"), "target"));
        Assert.assertFalse(CollectionUtils.contains(Collections.emptyList(), "target"));
    }
    
    @Test
    public void testIsEmpty() {
        Assert.assertFalse(CollectionUtils.isEmpty(Collections.singletonList("target")));
        Assert.assertTrue(CollectionUtils.isEmpty(Collections.emptyList()));
        Assert.assertTrue(CollectionUtils.isEmpty(null));
    }
    
    @Test
    public void testIsNotEmpty() {
        Assert.assertTrue(CollectionUtils.isNotEmpty(Collections.singletonList("target")));
        Assert.assertFalse(CollectionUtils.isNotEmpty(Collections.emptyList()));
        Assert.assertFalse(CollectionUtils.isNotEmpty(null));
    }
    
    @Test
    public void testGetOrDefault() {
        Assert.assertEquals("default", CollectionUtils.getOrDefault(Collections.emptyList(), 1, "default"));
        Assert.assertEquals("element",
                CollectionUtils.getOrDefault(Collections.singletonList("element"), 0, "default"));
    }
    
    @Test
    public void testList() {
        Assert.assertEquals(Arrays.asList(null, null, null), CollectionUtils.list(null, null, null));
        Assert.assertEquals(Arrays.asList("", "a", "b"), CollectionUtils.list("", "a", "b"));
        Assert.assertEquals(new ArrayList(), CollectionUtils.list());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testListNullPointerException() {
        CollectionUtils.list(null);
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
    public void testSet() {
        Set<Object> set = new HashSet<>();
        set.add(null);
        Assert.assertEquals(set, CollectionUtils.set(null, null, null));
        Assert.assertEquals(new LinkedHashSet(Arrays.asList("", "a", "b")), CollectionUtils.set("", "a", "b"));
        Assert.assertEquals(new HashSet(), CollectionUtils.set());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSetNullPointerException() {
        CollectionUtils.set(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetOnlyElementIllegalArgumentException() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6);
        CollectionUtils.getOnlyElement(list);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetOnlyElementIllegalArgumentException2() {
        CollectionUtils.getOnlyElement(null);
    }
    
    @Test(expected = NoSuchElementException.class)
    public void testGetOnlyElementNoSuchElementException() {
        List<Object> list = new ArrayList<>();
        CollectionUtils.getOnlyElement(list);
    }
    
    @Test
    public void testGetOnly() {
        List<Integer> list = Arrays.asList(1);
        int element = CollectionUtils.getOnlyElement(list);
        Assert.assertEquals(1, element);
    }
    
    @Test
    public void testIsListEqualForNull() {
        Assert.assertTrue(CollectionUtils.isListEqual(null, null));
        Assert.assertFalse(CollectionUtils.isListEqual(Collections.emptyList(), null));
        Assert.assertFalse(CollectionUtils.isListEqual(null, Collections.emptyList()));
    }
    
    @Test
    public void testIsListEqualForEquals() {
        List<String> list1 = Arrays.asList("1", "2", "3");
        List<String> list2 = Arrays.asList("1", "2", "3");
        Assert.assertTrue(CollectionUtils.isListEqual(list1, list1));
        Assert.assertTrue(CollectionUtils.isListEqual(list1, list2));
        Assert.assertTrue(CollectionUtils.isListEqual(list2, list1));
    }
    
    @Test
    public void testIsListEqualForNotEquals() {
        List<String> list1 = Arrays.asList("1", "2", "3");
        List<String> list2 = Arrays.asList("1", "2", "3", "4");
        List<String> list3 = Arrays.asList("1", "2", "3", "5");
        Assert.assertFalse(CollectionUtils.isListEqual(list1, list2));
        Assert.assertFalse(CollectionUtils.isListEqual(list2, list3));
    }
    
    @Test
    public void testIsMapEmpty() {
        Assert.assertTrue(CollectionUtils.isMapEmpty(null));
        Assert.assertTrue(CollectionUtils.isMapEmpty(Collections.emptyMap()));
    }
}
