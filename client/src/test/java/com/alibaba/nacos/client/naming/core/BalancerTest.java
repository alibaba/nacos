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

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancerTest {
    
    @Test
    void testGetHostByRandomWeightNull() {
        assertNull(Balancer.getHostByRandomWeight(null));
        assertNull(Balancer.getHostByRandomWeight(new ArrayList<>()));
    }
    
    @Test
    void testGetHostByRandomWeight() {
        List<Instance> list = new ArrayList<>();
        Instance instance1 = new Instance();
        list.add(instance1);
        final Instance actual = Balancer.getHostByRandomWeight(list);
        assertEquals(instance1, actual);
    }
    
    @Test
    void testSelectHost() {
        List<Instance> hosts = new ArrayList<>();
        Instance instance1 = new Instance();
        hosts.add(instance1);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(hosts);
        
        final Instance actual = Balancer.RandomByWeight.selectHost(serviceInfo);
        assertEquals(instance1, actual);
    }
    
    @Test
    void testSelectHostEmpty() {
        Throwable exception = assertThrows(IllegalStateException.class, () -> {
            ServiceInfo serviceInfo = new ServiceInfo();
            
            Balancer.RandomByWeight.selectHost(serviceInfo);
        });
        assertTrue(exception.getMessage().contains("no host to srv for serviceInfo: null"));
    }
}