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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceUtilTest {
    
    @AfterEach
    void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new ServiceUtil());
    }
    
    @Test
    void testSelectInstances() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setGroupName("groupName");
        serviceInfo.setName("serviceName");
        serviceInfo.setChecksum("checkSum");
        serviceInfo.setAllIps(false);
        ServiceInfo cluster = ServiceUtil.selectInstances(serviceInfo, "cluster");
        assertNotNull(cluster);
    }
    
    @Test
    void testTransferToConsoleResult() {
        ServiceDetailInfo serviceDetailInfo = new ServiceDetailInfo();
        serviceDetailInfo.setServiceName("serviceName");
        serviceDetailInfo.setGroupName("groupName");
        serviceDetailInfo.setProtectThreshold(0.7F);
        serviceDetailInfo.setMetadata(Collections.singletonMap("owner", "naming"));
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setHealthyCheckPort(8080);
        clusterInfo.setUseInstancePortForCheck(false);
        clusterInfo.setMetadata(Collections.singletonMap("zone", "z1"));
        Map<String, ClusterInfo> clusterMap = new HashMap<>(1);
        clusterMap.put("clusterA", clusterInfo);
        serviceDetailInfo.setClusterMap(clusterMap);
        
        ObjectNode result = (ObjectNode) ServiceUtil.transferToConsoleResult(serviceDetailInfo);
        
        ObjectNode service = (ObjectNode) result.get(FieldsConstants.SERVICE);
        assertEquals("serviceName", service.get(FieldsConstants.NAME).asText());
        assertEquals("groupName", service.get(FieldsConstants.GROUP_NAME).asText());
        assertEquals("naming", service.get(FieldsConstants.METADATA).get("owner").asText());
        ObjectNode cluster = (ObjectNode) result.get(FieldsConstants.CLUSTERS).get(0);
        assertEquals("clusterA", cluster.get(FieldsConstants.NAME).asText());
        assertEquals(8080, cluster.get("defaultCheckPort").asInt());
        assertFalse(cluster.get("useIpPort4Check").asBoolean());
        assertEquals("z1", cluster.get(FieldsConstants.METADATA).get("zone").asText());
    }
    
    @Test
    void testPageServiceName() {
        List<String> services = Arrays.asList("group@@serviceA", "serviceB", "group@@serviceC");
        
        assertEquals(Arrays.asList("serviceA", "serviceB"),
            ServiceUtil.pageServiceName(1, 2, services));
        assertEquals(Collections.singletonList("serviceC"),
            ServiceUtil.pageServiceName(2, 2, services));
        assertTrue(ServiceUtil.pageServiceName(3, 2, services).isEmpty());
        assertEquals(Collections.singletonList("serviceA"),
            ServiceUtil.pageServiceName(0, 1, services));
    }
    
    @Test
    void testSelectInstancesByHealthEnabledAndCluster() {
        Instance healthyEnabled = instance("1.1.1.1", "clusterA", true, true);
        Instance unhealthyEnabled = instance("1.1.1.2", "clusterA", false, true);
        Instance healthyDisabled = instance("1.1.1.3", "clusterB", true, false);
        ServiceInfo serviceInfo = serviceInfo(healthyEnabled, unhealthyEnabled, healthyDisabled);
        
        assertEquals(2, ServiceUtil.selectHealthyInstances(serviceInfo).getHosts().size());
        assertEquals(2, ServiceUtil.selectEnabledInstances(serviceInfo).getHosts().size());
        assertEquals(2, ServiceUtil.selectInstances(serviceInfo, "clusterA").getHosts().size());
        assertEquals(Collections.singletonList(healthyEnabled),
            ServiceUtil.selectInstances(serviceInfo, "clusterA", true).getHosts());
        assertEquals(Collections.singletonList(healthyEnabled),
            ServiceUtil.selectInstances(serviceInfo, "clusterA", true, true).getHosts());
    }
    
    @Test
    void testSelectInstancesWithHealthyProtectionBySubscriber() {
        ServiceInfo serviceInfo =
            serviceInfo(instance("1.1.1.1", "clusterA", true, true),
                instance("1.1.1.2", "clusterB", false, true));
        Subscriber subscriber =
            new Subscriber("2.2.2.2:8848", "agent", "app", "2.2.2.2", "namespaceId",
                "groupName@@serviceName", 8848, "clusterA");
        
        ServiceInfo result =
            ServiceUtil.selectInstancesWithHealthyProtection(serviceInfo, null, subscriber);
        
        assertEquals(1, result.getHosts().size());
        assertEquals("clusterA", result.getHosts().get(0).getClusterName());
    }
    
    @Test
    void testSelectInstancesWithHealthyProtectionWithoutMetadata() {
        ServiceInfo serviceInfo =
            serviceInfo(instance("1.1.1.1", "clusterA", true, true),
                instance("1.1.1.2", "clusterB", false, true));
        
        ServiceInfo result =
            ServiceUtil.selectInstancesWithHealthyProtection(serviceInfo, null, "clusterA", false,
                false, "2.2.2.2");
        
        assertFalse(result.isReachProtectionThreshold());
        assertEquals(1, result.getHosts().size());
        assertEquals("clusterA", result.getHosts().get(0).getClusterName());
    }
    
    @Test
    void testSelectInstancesWithHealthyProtection() {
        ServiceInfo serviceInfo =
            serviceInfo(instance("1.1.1.1", "clusterA", true, true),
                instance("1.1.1.2", "clusterA", false, true));
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(0.6F);
        SelectorManager selectorManager = Mockito.mock(SelectorManager.class);
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);
        ApplicationUtils.injectContext(context);
        Mockito.when(context.getBean(SelectorManager.class)).thenReturn(selectorManager);
        Mockito.when(selectorManager.select(Mockito.any(), Mockito.eq("2.2.2.2"),
            Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(2));
        
        ServiceInfo result =
            ServiceUtil.selectInstancesWithHealthyProtection(serviceInfo, serviceMetadata,
                "clusterA", false, false, "2.2.2.2");
        
        assertTrue(result.isReachProtectionThreshold());
        assertEquals(2, result.getHosts().size());
        assertTrue(result.getHosts().stream().allMatch(Instance::isHealthy));
    }
    
    @Test
    void testSelectInstancesWithHealthyProtectionRecomputesAfterSelector() {
        Instance healthy = instance("1.1.1.1", "clusterA", true, true);
        Instance unhealthy = instance("1.1.1.2", "clusterA", false, true);
        ServiceInfo serviceInfo =
            serviceInfo(healthy, unhealthy, instance("1.1.1.3", "clusterA", false, true));
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(-1F);
        SelectorManager selectorManager = Mockito.mock(SelectorManager.class);
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);
        ApplicationUtils.injectContext(context);
        Mockito.when(context.getBean(SelectorManager.class)).thenReturn(selectorManager);
        Mockito.when(selectorManager.select(Mockito.any(), Mockito.eq("2.2.2.2"),
            Mockito.anyList())).thenReturn(Arrays.asList(healthy, unhealthy));
        
        ServiceInfo result =
            ServiceUtil.selectInstancesWithHealthyProtection(serviceInfo, serviceMetadata,
                "clusterA", false, false, "2.2.2.2");
        
        assertFalse(result.isReachProtectionThreshold());
        assertEquals(Arrays.asList(healthy, unhealthy), result.getHosts());
    }
    
    private ServiceInfo serviceInfo(Instance... instances) {
        ServiceInfo result = new ServiceInfo();
        result.setGroupName("groupName");
        result.setName("serviceName");
        result.setCacheMillis(1000L);
        result.setHosts(Arrays.asList(instances));
        return result;
    }
    
    private Instance instance(String ip, String cluster, boolean healthy, boolean enabled) {
        Instance result = new Instance();
        result.setIp(ip);
        result.setPort(8848);
        result.setClusterName(cluster);
        result.setHealthy(healthy);
        result.setEnabled(enabled);
        return result;
    }
}
