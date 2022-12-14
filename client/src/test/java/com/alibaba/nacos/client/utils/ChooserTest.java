/*
 *
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
 *
 */

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.utils.Chooser;
import com.alibaba.nacos.client.naming.utils.Pair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertTrue;

public class ChooserTest {
    
    //Test the correctness of Chooser, the weight of the final selected instance must be greater than 0
    @Test
    public void testChooser() {
        List<Instance> hosts = getInstanceList();
        Instance target = getRandomInstance(hosts);
        assertTrue(hosts.contains(target) && target.getWeight() > 0);
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
    
    // If there is only one instance whose weight is not zero, it will be selected
    @Test
    public void testOnlyOneInstanceWeightIsNotZero() {
        List<Instance> hosts = getOneInstanceNotZeroList();
        
        Instance target = getRandomInstance(hosts);
        assertTrue(target.getWeight() > 0);
    }
    
    // getOneInstanceNotZeroList
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
    
    // Throw an IllegalStateException when all instances have a weight of zero.
    @Test
    public void testInstanceWeightAllZero() {
        List<Instance> hosts = getInstanceWeightAllZero();
        
        try {
            getRandomInstance(hosts);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
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
