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

package com.alibaba.nacos.client.naming.beat;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class BeatInfoTest {
    
    @Test
    public void testGetterAndSetter() {
        BeatInfo info = new BeatInfo();
        
        String ip = "1.1.1.1";
        info.setIp(ip);
        int port = 10000;
        info.setPort(port);
        
        double weight = 1.0;
        info.setWeight(weight);
        String serviceName = "serviceName";
        info.setServiceName(serviceName);
        String cluster = "cluster1";
        info.setCluster(cluster);
        Map<String, String> meta = new HashMap<>();
        meta.put("a", "b");
        info.setMetadata(meta);
        long period = 100;
        info.setPeriod(period);
        info.setScheduled(true);
        info.setStopped(true);
        
        Assert.assertEquals(ip, info.getIp());
        Assert.assertEquals(port, info.getPort());
        Assert.assertEquals(weight, info.getWeight(), 0.1);
        Assert.assertEquals(serviceName, info.getServiceName());
        Assert.assertEquals(meta, info.getMetadata());
        Assert.assertEquals(period, info.getPeriod());
        Assert.assertTrue(info.isScheduled());
        Assert.assertTrue(info.isStopped());
    }
    
    @Test
    public void testToString() {
        BeatInfo info = new BeatInfo();
        
        String ip = "1.1.1.1";
        info.setIp(ip);
        int port = 10000;
        info.setPort(port);
        
        double weight = 1.0;
        info.setWeight(weight);
        String serviceName = "serviceName";
        info.setServiceName(serviceName);
        String cluster = "cluster1";
        info.setCluster(cluster);
        Map<String, String> meta = new HashMap<>();
        meta.put("a", "b");
        info.setMetadata(meta);
        long period = 100;
        info.setPeriod(period);
        info.setScheduled(true);
        info.setStopped(true);
        String expect = "BeatInfo{port=10000, ip='1.1.1.1', " + "weight=1.0, serviceName='serviceName',"
                + " cluster='cluster1', metadata={a=b}," + " scheduled=true, period=100, stopped=true}";
        Assert.assertEquals(expect, info.toString());
    }
    
}