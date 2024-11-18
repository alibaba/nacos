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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChooserTest {
    
    @Test
    void testChooser() {
        //Test the correctness of Chooser, the weight of the final selected instance must be greater than 0
        List<Instance> hosts = getInstanceList();
        Instance target = getRandomInstance(hosts);
        assertTrue(hosts.contains(target) && target.getWeight() > 0);
    }
    
    @Test
    void testChooserRandomForEmptyList() {
        Chooser<String, String> chooser = new Chooser<>("test");
        assertEquals("test", chooser.getUniqueKey());
        assertNull(chooser.random());
    }
    
    @Test
    void testChooserRandomForOneSizeList() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        String actual = chooser.random();
        assertNotNull(actual);
        assertEquals("test", actual);
    }
    
    @Test
    void testChooserRandom() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        list.add(new Pair<>("test2", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        String actual = chooser.random();
        assertNotNull(actual);
        assertTrue(actual.equals("test") || actual.equals("test2"));
    }
    
    @Test
    void testOnlyOneInstanceWeightIsNotZero() {
        // If there is only one instance whose weight is not zero, it will be selected
        List<Instance> hosts = getOneInstanceNotZeroList();
        
        Instance target = getRandomInstance(hosts);
        assertTrue(target.getWeight() > 0);
    }
    
    @Test
    void testInstanceWeightAllZero() {
        // Throw an IllegalStateException when all instances have a weight of zero.
        List<Instance> hosts = getInstanceWeightAllZero();
        
        try {
            getRandomInstance(hosts);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }
    
    @Test
    void testRandomWithWeightForNaNAndInfinity() {
        assertThrows(IllegalStateException.class, () -> {
            List<Pair<String>> list = new LinkedList<>();
            list.add(new Pair<>("test", Double.NaN));
            list.add(new Pair<>("test2", Double.POSITIVE_INFINITY));
            new Chooser<>("test", list);
        });
    }
    
    @Test
    void testRefresh() {
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
    void testEqualsHashCode() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        list.add(new Pair<>("test2", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        assertEquals("test".hashCode(), chooser.hashCode());
        assertEquals(chooser, chooser);
        assertNotEquals(null, chooser);
        assertNotEquals("test", chooser);
        Chooser<String, String> chooser1 = new Chooser<>(null, null);
        assertNotEquals(chooser, chooser1);
        assertNotEquals(chooser1, chooser);
        Chooser<String, String> chooser2 = new Chooser<>("test", Collections.emptyList());
        assertNotEquals(chooser, chooser2);
        assertNotEquals(chooser2, chooser);
        Chooser<String, String> chooser3 = new Chooser<>("test1", list);
        assertNotEquals(chooser, chooser3);
        Chooser<String, String> chooser4 = new Chooser<>("test", list);
        assertEquals(chooser, chooser4);
    }
    
    @Test
    void testRefEqualsHashCode() {
        List<Pair<String>> list = new LinkedList<>();
        list.add(new Pair<>("test", 1));
        list.add(new Pair<>("test2", 1));
        Chooser<String, String> chooser = new Chooser<>("test", list);
        Chooser.Ref ref = chooser.getRef();
        assertEquals(list.hashCode(), ref.hashCode());
        assertEquals(ref, ref);
        assertNotEquals(null, ref);
        assertNotEquals(ref, chooser);
        Chooser.Ref ref1 = new Chooser<>("test", null).getRef();
        assertNotEquals(ref, ref1);
        assertNotEquals(ref1, ref);
        Chooser.Ref ref2 = new Chooser<>("test", list).getRef();
        assertEquals(ref, ref2);
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
