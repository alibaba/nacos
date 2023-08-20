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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.selector.SelectorFactory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

public class InstancesChangeNotifierTest {

    @Test
    public void testRegisterListener() {
        String eventScope = "scope-001";
        String group = "a";
        String name = "b";
        String clusterStr = "c";
        List<String> clusters = Collections.singletonList(clusterStr);
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier(eventScope);
        EventListener listener = Mockito.mock(EventListener.class);
        NamingSelector selector = SelectorFactory.newClusterSelector(clusters);
        instancesChangeNotifier.registerListener(group, name, selector, listener);
        List<ServiceInfo> subscribeServices = instancesChangeNotifier.getSubscribeServices();
        Assert.assertEquals(1, subscribeServices.size());
        Assert.assertEquals(group, subscribeServices.get(0).getGroupName());
        Assert.assertEquals(name, subscribeServices.get(0).getName());

        List<Instance> hosts = new ArrayList<>();
        Instance ins = new Instance();
        hosts.add(ins);
        InstancesDiff diff = new InstancesDiff();
        diff.setAddedInstances(hosts);
        InstancesChangeEvent event = new InstancesChangeEvent(eventScope, name, group, clusterStr, hosts, diff);
        Assert.assertEquals(true, instancesChangeNotifier.scopeMatches(event));
    }

    @Test
    public void testDeregisterListener() {
        String eventScope = "scope-001";
        String group = "a";
        String name = "b";
        String clusterStr = "c";
        List<String> clusters = Collections.singletonList(clusterStr);
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier(eventScope);
        EventListener listener = Mockito.mock(EventListener.class);
        NamingSelector selector = SelectorFactory.newClusterSelector(clusters);
        instancesChangeNotifier.registerListener(group, name, selector, listener);
        List<ServiceInfo> subscribeServices = instancesChangeNotifier.getSubscribeServices();
        Assert.assertEquals(1, subscribeServices.size());

        instancesChangeNotifier.deregisterListener(group, name, selector, listener);

        List<ServiceInfo> subscribeServices2 = instancesChangeNotifier.getSubscribeServices();
        Assert.assertEquals(0, subscribeServices2.size());
    }

    @Test
    public void testIsSubscribed() {
        String eventScope = "scope-001";
        String group = "a";
        String name = "b";
        String clusterStr = "c";
        List<String> clusters = Collections.singletonList(clusterStr);
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier(eventScope);
        EventListener listener = Mockito.mock(EventListener.class);
        NamingSelector selector = SelectorFactory.newClusterSelector(clusters);
        Assert.assertFalse(instancesChangeNotifier.isSubscribed(group, name));

        instancesChangeNotifier.registerListener(group, name, selector, listener);
        Assert.assertTrue(instancesChangeNotifier.isSubscribed(group, name));
    }

    @Test
    public void testOnEvent() {
        String eventScope = "scope-001";
        String group = "a";
        String name = "b";
        String clusterStr = "c";
        List<String> clusters = Collections.singletonList(clusterStr);
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier(eventScope);
        NamingSelector selector = SelectorFactory.newClusterSelector(clusters);
        EventListener listener = Mockito.mock(EventListener.class);

        instancesChangeNotifier.registerListener(group, name, selector, listener);
        Instance instance = new Instance();
        InstancesDiff diff = new InstancesDiff(null, Collections.singletonList(instance), null);
        instance.setClusterName("c");
        InstancesChangeEvent event1 = new InstancesChangeEvent(null, name, group, clusterStr,
                Collections.emptyList(), diff);
        instancesChangeNotifier.onEvent(event1);
        Mockito.verify(listener, times(1)).onEvent(any());
    }

    @Test
    public void testSubscribeType() {
        String eventScope = "scope-001";
        InstancesChangeNotifier instancesChangeNotifier = new InstancesChangeNotifier(eventScope);
        Assert.assertEquals(InstancesChangeEvent.class, instancesChangeNotifier.subscribeType());
    }
}