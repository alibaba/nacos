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

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstancesChangeEventTest {
    
    @Test
    public void testGetServiceName() {
        String eventScope = "scope-001";
        String serviceName = "a";
        String groupName = "b";
        String clusters = "c";
        List<Instance> hosts = new ArrayList<>();
        Instance ins = new Instance();
        hosts.add(ins);
        Set<Instance> modHosts = new HashSet<>();
        Instance modIns = new Instance();
        modHosts.add(modIns);
        Set<Instance> newHosts = new HashSet<>();
        Instance newIns = new Instance();
        newHosts.add(newIns);
        Set<Instance> removeHosts = new HashSet<>();
        Instance removeIns = new Instance();
        removeHosts.add(removeIns);
        InstancesChangeEvent event = new InstancesChangeEvent(eventScope, serviceName, groupName, clusters, hosts, modHosts, newHosts, removeHosts);
        Assert.assertEquals(eventScope, event.scope());
        Assert.assertEquals(serviceName, event.getServiceName());
        Assert.assertEquals(clusters, event.getClusters());
        Assert.assertEquals(groupName, event.getGroupName());
        List<Instance> hosts1 = event.getHosts();
        Assert.assertEquals(hosts.size(), hosts1.size());
        Assert.assertEquals(hosts.get(0), hosts1.get(0));
        Set<Instance> hosts2 = event.getModHosts();
        Assert.assertEquals(modHosts.size(), hosts2.size());
        Assert.assertEquals(modHosts.stream().findFirst().orElse(null), hosts2.stream().findFirst().orElse(null));
        Set<Instance> hosts3 = event.getNewHosts();
        Assert.assertEquals(newHosts.size(), hosts3.size());
        Assert.assertEquals(newHosts.stream().findFirst().orElse(null), hosts3.stream().findFirst().orElse(null));
        Set<Instance> hosts4 = event.getRemvHosts();
        Assert.assertEquals(removeHosts.size(), hosts4.size());
        Assert.assertEquals(removeHosts.stream().findFirst().orElse(null), hosts4.stream().findFirst().orElse(null));
    }
}