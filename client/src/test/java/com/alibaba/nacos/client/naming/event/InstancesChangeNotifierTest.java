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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

public class InstancesChangeNotifierTest {
    
    @Test
    public void testRegisterListener() {
        String group = "a";
        String name = "b";
        String clusters = "c";
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier();
        EventListener listener = Mockito.mock(EventListener.class);
        instancesChangeNotifier.registerListener(group, name, clusters, listener);
        List<ServiceInfo> subscribeServices = instancesChangeNotifier.getSubscribeServices();
        Assert.assertEquals(1, subscribeServices.size());
        Assert.assertEquals(group, subscribeServices.get(0).getGroupName());
        Assert.assertEquals(name, subscribeServices.get(0).getName());
        Assert.assertEquals(clusters, subscribeServices.get(0).getClusters());
        
    }
    
    @Test
    public void testDeregisterListener() {
        String group = "a";
        String name = "b";
        String clusters = "c";
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier();
        EventListener listener = Mockito.mock(EventListener.class);
        instancesChangeNotifier.registerListener(group, name, clusters, listener);
        List<ServiceInfo> subscribeServices = instancesChangeNotifier.getSubscribeServices();
        Assert.assertEquals(1, subscribeServices.size());
        
        instancesChangeNotifier.deregisterListener(group, name, clusters, listener);
        
        List<ServiceInfo> subscribeServices2 = instancesChangeNotifier.getSubscribeServices();
        Assert.assertEquals(1, subscribeServices2.size());
    }
    
    @Test
    public void testIsSubscribed() {
        String group = "a";
        String name = "b";
        String clusters = "c";
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier();
        EventListener listener = Mockito.mock(EventListener.class);
        Assert.assertFalse(instancesChangeNotifier.isSubscribed(group, name, clusters));
        
        instancesChangeNotifier.registerListener(group, name, clusters, listener);
        Assert.assertTrue(instancesChangeNotifier.isSubscribed(group, name, clusters));
    }
    
    @Test
    public void testOnEvent() {
        String group = "a";
        String name = "b";
        String clusters = "c";
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier();
        EventListener listener = Mockito.mock(EventListener.class);
        
        instancesChangeNotifier.registerListener(group, name, clusters, listener);
        InstancesChangeEvent event1 = Mockito.mock(InstancesChangeEvent.class);
        Mockito.when(event1.getClusters()).thenReturn(clusters);
        Mockito.when(event1.getGroupName()).thenReturn(group);
        Mockito.when(event1.getServiceName()).thenReturn(name);
        
        instancesChangeNotifier.onEvent(event1);
        Mockito.verify(listener, times(1)).onEvent(any());
    }
    
    @Test
    public void testSubscribeType() {
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier();
        Assert.assertEquals(InstancesChangeEvent.class, instancesChangeNotifier.subscribeType());
    }
}