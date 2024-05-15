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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstancesChangeEventTest {
    
    @Test
    void testGetServiceName() {
        String eventScope = "scope-001";
        String serviceName = "a";
        String groupName = "b";
        String clusters = "c";
        List<Instance> hosts = new ArrayList<>();
        Instance ins = new Instance();
        hosts.add(ins);
        InstancesChangeEvent event = new InstancesChangeEvent(eventScope, serviceName, groupName, clusters, hosts);
        assertEquals(eventScope, event.scope());
        assertEquals(serviceName, event.getServiceName());
        assertEquals(clusters, event.getClusters());
        assertEquals(groupName, event.getGroupName());
        List<Instance> hosts1 = event.getHosts();
        assertEquals(hosts.size(), hosts1.size());
        assertEquals(hosts.get(0), hosts1.get(0));
    }
}