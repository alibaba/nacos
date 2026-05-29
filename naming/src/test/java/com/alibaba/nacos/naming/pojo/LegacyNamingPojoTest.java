/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.pojo;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class LegacyNamingPojoTest {
    
    @Test
    void testClusterInfoFromMaintainerClusterInfo() {
        com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo source =
            new com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo();
        source.setClusterName("cluster");
        source.setHealthChecker(new AbstractHealthChecker.None());
        source.setMetadata(Collections.singletonMap("env", "test"));
        source.setHosts(Collections.singletonList(createInstance()));
        
        ClusterInfo actual = ClusterInfo.from(source);
        
        assertEquals("cluster", actual.getClusterName());
        assertSame(source.getHealthChecker(), actual.getHealthChecker());
        assertEquals("test", actual.getMetadata().get("env"));
        assertEquals(1, actual.getHosts().size());
        IpAddressInfo host = actual.getHosts().get(0);
        assertEquals("1.1.1.1", host.getIp());
        assertEquals(8848, host.getPort());
        assertEquals(2.0, host.getWeight());
        assertEquals("v1", host.getMetadata().get("version"));
        assertTrue(host.isEnabled());
        assertFalse(host.isValid());
    }
    
    @Test
    void testServiceDetailInfoFromMaintainerServiceDetailInfo() {
        com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo clusterInfo =
            new com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo();
        clusterInfo.setClusterName("cluster");
        clusterInfo.setMetadata(Collections.singletonMap("cluster", "metadata"));
        clusterInfo.setHosts(Collections.singletonList(createInstance()));
        com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo source =
            new com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo();
        source.setNamespaceId("namespace");
        source.setServiceName("service");
        source.setGroupName("group");
        source.setMetadata(Collections.singletonMap("service", "metadata"));
        source.setProtectThreshold(0.8F);
        source.setEphemeral(true);
        source.setClusterMap(Collections.singletonMap("cluster", clusterInfo));
        
        ServiceDetailInfo actual = ServiceDetailInfo.from(source);
        
        assertEquals("namespace", actual.getNamespace());
        assertEquals("service", actual.getServiceName());
        assertEquals("group", actual.getGroupName());
        assertEquals("metadata", actual.getMetadata().get("service"));
        assertEquals(0.8F, actual.getProtectThreshold());
        assertTrue(actual.isEphemeral());
        assertEquals(1, actual.getClusterMap().size());
        assertEquals("cluster", actual.getClusterMap().get("cluster").getClusterName());
    }
    
    @Test
    void testIpAddressInfoAccessors() {
        IpAddressInfo info = new IpAddressInfo();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("k", "v");
        
        info.setValid(true);
        info.setEnabled(true);
        info.setIp("1.1.1.1");
        info.setPort(8848);
        info.setWeight(1.5);
        info.setMetadata(metadata);
        
        assertTrue(info.isValid());
        assertTrue(info.isEnabled());
        assertEquals("1.1.1.1", info.getIp());
        assertEquals(8848, info.getPort());
        assertEquals(1.5, info.getWeight());
        assertEquals(metadata, info.getMetadata());
    }
    
    @Test
    void testServiceNameViewAccessors() {
        ServiceNameView view = new ServiceNameView();
        
        view.setCount(2);
        view.setServices(Collections.singletonList("group@@service"));
        
        assertEquals(2, view.getCount());
        assertEquals(Collections.singletonList("group@@service"), view.getServices());
    }
    
    private Instance createInstance() {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        instance.setWeight(2.0);
        instance.setMetadata(Collections.singletonMap("version", "v1"));
        instance.setEnabled(true);
        instance.setHealthy(false);
        return instance;
    }
}
