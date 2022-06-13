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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.Service;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServiceUtilTest {
    
    @Test
    public void testPageServiceName() {
        Map<String, Service> input = new HashMap<>();
        input.put("Group@@Service", new Service());
        input.put("Service", new Service());
        List<String> actual = ServiceUtil.pageServiceName(1, 20, input);
        assertEquals(2, actual.size());
        assertEquals("Service", actual.get(0));
        assertEquals("Service", actual.get(1));
    }
    
    @Test
    public void testSelectServiceWithGroupName() {
        Service service1 = new Service();
        service1.setEnabled(true);
        service1.setName("serviceName");
        service1.setGroupName("groupName");
        service1.setAppName("appName");
        service1.setNamespaceId("namespaceId");
        service1.setResetWeight(true);
        Map<String, Service> services = new HashMap<>();
        services.put("service1", service1);
        Map<String, Service> resultMap = ServiceUtil.selectServiceWithGroupName(services, "groupName");
        assertNotNull(resultMap);
    }
    
    @Test
    public void testSelectServiceBySelector() {
        Service service1 = new Service();
        service1.setEnabled(true);
        service1.setName("serviceName");
        service1.setGroupName("groupName");
        service1.setAppName("appName");
        service1.setNamespaceId("namespaceId");
        service1.setResetWeight(true);
        Map<String, Service> serviceMap = new HashMap<>();
        serviceMap.put("service1", service1);
        Map<String, Service> resultMap = ServiceUtil.selectServiceBySelector(serviceMap,
                "{\"type\":\"label\",\"expression\":\"msg\"}");
        assertNotNull(resultMap);
    }
    
    @Test
    public void testSelectInstances() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setGroupName("groupName");
        serviceInfo.setName("serviceName");
        serviceInfo.setChecksum("checkSum");
        serviceInfo.setAllIPs(false);
        ServiceInfo cluster = ServiceUtil.selectInstances(serviceInfo, "cluster");
        assertNotNull(cluster);
    }
}
