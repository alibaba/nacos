/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChooserTest {
    
    @Test
    public void testChooser() {
        //Test the correctness of Chooser, the weight of the final selected instance must be greater than 0
        List<Instance> hosts = getInstanceList();
        Instance target = getRandomInstance(hosts);
        assertTrue(hosts.contains(target) && target.getWeight() > 0);
    }
    
    @Test
    public void testChooserRandomForEmptyList() {
        Chooser<String, String> chooser = new Chooser<>("test");
        assertEquals("test", chooser.getUniqueKey());
        assertNull(chooser.random());
    }
    
    @Test
    public void testChooserRandomForOneSizeList() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        String actual = chooser.random();
        assertNotNull(actual);
        assertEquals("test", actual);
    }
    
    @Test
    public void testChooserRandom() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        list.add(new Pair<>("test2", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        String actual = chooser.random();
        assertNotNull(actual);
        assertTrue(actual.equals("test") || actual.equals("test2"));
    }
    
    @Test
    public void testOnlyOneInstanceWeightIsNotZero() {
        // If there is only one instance whose weight is not zero, it will be selected
        List<Instance> hosts = getOneInstanceNotZeroList();
        
        Instance target = getRandomInstance(hosts);
        assertTrue(target.getWeight() > 0);
    }
    
    @Test
    public void testInstanceWeightAllZero() {
        // Throw an IllegalStateException when all instances have a weight of zero.
        List<Instance> hosts = getInstanceWeightAllZero();
        
        try {
            getRandomInstance(hosts);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }
    
    @Test(expected = IllegalStateException.class)
    public void testRandomWithWeightForNaNAndInfinity() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", Double.NaN));
        list.add(new Pair<>("test2", Double.POSITIVE_INFINITY));
        new Chooser<>("test", list);
    }
    
    @Test
    public void testRefresh() {
        Chooser<String, String> chooser = new Chooser<>("test");
        assertEquals("test", chooser.getUniqueKey());
        assertNull(chooser.random());
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        chooser.refresh(list);
        String actual = chooser.random();
        assertNotNull(actual);
        assertEquals("test", actual);
    }
    
    @Test
    public void testEqualsHashCode() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        list.add(new Pair<>("test2", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        assertEquals("test".hashCode(), chooser.hashCode());
        assertTrue(chooser.equals(chooser));
        assertFalse(chooser.equals(null));
        assertFalse(chooser.equals("test"));
        Chooser<String, String> chooser1 = new Chooser<>(null, null);
        assertFalse(chooser.equals(chooser1));
        assertFalse(chooser1.equals(chooser));
        Chooser<String, String> chooser2 = new Chooser<>("test", Collections.emptyList());
        assertFalse(chooser.equals(chooser2));
        assertFalse(chooser2.equals(chooser));
        Chooser<String, String> chooser3 = new Chooser<>("test1", list);
        assertFalse(chooser.equals(chooser3));
        Chooser<String, String> chooser4 = new Chooser<>("test", list);
        assertTrue(chooser.equals(chooser4));
    }
    
    @Test
    public void testRefEqualsHashCode() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        list.add(new Pair<>("test2", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        Chooser.Ref ref = chooser.getRef();
        assertEquals(list.hashCode(), ref.hashCode());
        assertTrue(ref.equals(ref));
        assertFalse(ref.equals(null));
        assertFalse(ref.equals(chooser));
        Chooser.Ref ref1 = new Chooser<>("test", null).getRef();
        assertFalse(ref.equals(ref1));
        assertFalse(ref1.equals(ref));
        Chooser.Ref ref2 = new Chooser<>("test", list).getRef();
        assertTrue(ref.equals(ref2));
    }
    
    private List<Instance> getInstanceList() {
        List<Instance> list = new ArrayList<>();
        int size = ThreadLocalRandom.current().nextInt(0, 1000);
        for (int i = 0; i < size; i++) {
            Instance instance = new Instance();
            instance.setInstanceId(String.valueOf(i));
            instance.setWeight(i);
            list.add(instance);
        }
        return list;
    }
    
    private List<Instance> getOneInstanceNotZeroList() {
        List<Instance> list = new ArrayList<>();
        int size = ThreadLocalRandom.current().nextInt(0, 1000);
        int notZeroIndex = ThreadLocalRandom.current().nextInt(0, size - 1);
        
        for (int i = 0; i < size; i++) {
            Instance instance = new Instance();
            instance.setInstanceId(String.valueOf(i));
            if (i == notZeroIndex) {
                instance.setWeight(notZeroIndex + 1);
            } else {
                instance.setWeight(0);
            }
            list.add(instance);
        }
        return list;
    }
    
    private List<Instance> getInstanceWeightAllZero() {
        List<Instance> list = new ArrayList<>();
        int size = ThreadLocalRandom.current().nextInt(0, 1000);
        
        for (int i = 0; i < size; i++) {
            Instance instance = new Instance();
            instance.setInstanceId(String.valueOf(i));
            instance.setWeight(0);
            list.add(instance);
        }
        return list;
    }
    
    private Instance getRandomInstance(List<Instance> hosts) {
        List<Pair<Instance>> hostsWithWeight = new ArrayList<>();
        for (Instance host : hosts) {
            if (host.isHealthy()) {
                hostsWithWeight.add(new Pair<>(host, host.getWeight()));
            }
        }
        Chooser<String, Instance> vipChooser = new Chooser<>("www.taobao.com", hostsWithWeight);
        
        return vipChooser.randomWithWeight();
    }
}
