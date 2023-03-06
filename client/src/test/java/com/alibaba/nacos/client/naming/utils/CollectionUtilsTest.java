/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CollectionUtilsTest {
    
    @Test
    public void testSubtract() {
        List<String> subtract = (List<String>) CollectionUtils
                .subtract(Arrays.asList("a", "b"), Arrays.asList("b", "c"));
        Assert.assertEquals(1, subtract.size());
        Assert.assertEquals("a", subtract.get(0));
    }
    
    @Test
    public void testGetCardinalityMap() {
        List<String> list1 = Arrays.asList("2", "2", "3");
        Map<String, Integer> map1 = CollectionUtils.getCardinalityMap(list1);
        Assert.assertEquals(2, map1.size());
        Assert.assertEquals(2, map1.get("2").intValue());
        Assert.assertEquals(1, map1.get("3").intValue());
        
    }
    
    @Test
    public void testIsEqualCollection() {
        List<String> list1 = Arrays.asList("2", "2", "3");
        List<String> list2 = Arrays.asList("3", "2", "2");
        List<String> list3 = Arrays.asList("3", "2", "3");
        Assert.assertTrue(CollectionUtils.isEqualCollection(list1, list2));
        Assert.assertFalse(CollectionUtils.isEqualCollection(list1, list3));
        
    }
    
    @Test
    public void testIsEmpty() {
        Assert.assertTrue(CollectionUtils.isEmpty(null));
        Assert.assertTrue(CollectionUtils.isEmpty(new ArrayList<String>()));
        Assert.assertFalse(CollectionUtils.isEmpty(Arrays.asList("aa")));
    }
}