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

package com.alibaba.nacos.naming.model.vo;

import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("deprecation")
class NamingModelVoTest {
    
    @Test
    void testMetricsInfoVoAccessorsAndTransfer() {
        MetricsInfoVo source = new MetricsInfoVo();
        source.setStatus("UP");
        source.setServiceCount(1);
        source.setInstanceCount(2);
        source.setSubscribeCount(3);
        source.setClientCount(4);
        source.setConnectionBasedClientCount(5);
        source.setEphemeralIpPortClientCount(6);
        source.setPersistentIpPortClientCount(7);
        source.setResponsibleClientCount(8);
        source.setCpu(0.1F);
        source.setLoad(0.2F);
        source.setMem(0.3F);
        
        assertEquals("UP", source.getStatus());
        assertEquals(1, source.getServiceCount());
        assertEquals(2, source.getInstanceCount());
        assertEquals(3, source.getSubscribeCount());
        assertEquals(4, source.getClientCount());
        assertEquals(5, source.getConnectionBasedClientCount());
        assertEquals(6, source.getEphemeralIpPortClientCount());
        assertEquals(7, source.getPersistentIpPortClientCount());
        assertEquals(8, source.getResponsibleClientCount());
        assertEquals(0.1F, source.getCpu());
        assertEquals(0.2F, source.getLoad());
        assertEquals(0.3F, source.getMem());
        
        MetricsInfo actual = MetricsInfoVo.toNewMetricsInfo(source);
        assertEquals(source.getStatus(), actual.getStatus());
        assertEquals(source.getServiceCount(), actual.getServiceCount());
        assertEquals(source.getInstanceCount(), actual.getInstanceCount());
        assertEquals(source.getSubscribeCount(), actual.getSubscribeCount());
        assertEquals(source.getClientCount(), actual.getClientCount());
        assertEquals(source.getConnectionBasedClientCount(),
            actual.getConnectionBasedClientCount());
        assertEquals(source.getEphemeralIpPortClientCount(),
            actual.getEphemeralIpPortClientCount());
        assertEquals(source.getPersistentIpPortClientCount(),
            actual.getPersistentIpPortClientCount());
        assertEquals(source.getResponsibleClientCount(), actual.getResponsibleClientCount());
    }
    
    @Test
    void testInstanceDetailInfoVoAccessors() {
        InstanceDetailInfoVo actual = new InstanceDetailInfoVo();
        actual.setServiceName("service");
        actual.setIp("1.1.1.1");
        actual.setPort(8848);
        actual.setClusterName("cluster");
        actual.setWeight(1.5);
        actual.setHealthy(true);
        actual.setInstanceId("instanceId");
        actual.setMetadata(Collections.singletonMap("k", "v"));
        
        assertEquals("service", actual.getServiceName());
        assertEquals("1.1.1.1", actual.getIp());
        assertEquals(8848, actual.getPort());
        assertEquals("cluster", actual.getClusterName());
        assertEquals(1.5, actual.getWeight());
        assertEquals(true, actual.getHealthy());
        assertEquals("instanceId", actual.getInstanceId());
        assertEquals("v", actual.getMetadata().get("k"));
    }
}
