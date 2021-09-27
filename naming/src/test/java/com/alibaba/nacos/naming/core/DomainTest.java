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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteEventListener;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

public class DomainTest extends BaseTest {
    
    private Service service;
    
    @Mock
    private DoubleWriteEventListener doubleWriteEventListener;
    
    @Before
    public void before() {
        super.before();
        when(context.getBean(DoubleWriteEventListener.class)).thenReturn(doubleWriteEventListener);
        service = new Service();
        service.setName("nacos.service.1");
        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster);
        mockInjectPushServer();
    }
    
    @Test
    public void updateDomain() {
        
        Service newDomain = new Service();
        newDomain.setName("nacos.service.1");
        newDomain.setProtectThreshold(0.7f);
        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, newDomain);
        newDomain.addCluster(cluster);
        
        service.update(newDomain);
        
        Assert.assertEquals(0.7f, service.getProtectThreshold(), 0.0001f);
    }
    
    @Test
    public void addCluster() {
        Cluster cluster = new Cluster("nacos-cluster-1", service);
        
        service.addCluster(cluster);
        
        Map<String, Cluster> clusterMap = service.getClusterMap();
        Assert.assertNotNull(clusterMap);
        Assert.assertEquals(2, clusterMap.size());
        Assert.assertTrue(clusterMap.containsKey("nacos-cluster-1"));
    }
    
    @Test
    public void updateIps() throws Exception {
        
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(1234);
        List<Instance> list = new ArrayList<Instance>();
        list.add(instance);
        
        Instances instances = new Instances();
        
        instances.setInstanceList(list);
        
        service.onChange("iplist", instances);
        
        List<Instance> ips = service.allIPs();
        
        Assert.assertNotNull(ips);
        Assert.assertEquals(1, ips.size());
        Assert.assertEquals("1.1.1.1", ips.get(0).getIp());
        Assert.assertEquals(1234, ips.get(0).getPort());
    }
}
