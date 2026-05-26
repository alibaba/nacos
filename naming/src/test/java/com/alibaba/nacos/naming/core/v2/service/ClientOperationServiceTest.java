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

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.naming.constants.Constants.CUSTOM_INSTANCE_ID;
import static com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_ENABLE;
import static com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_WEIGHT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOperationServiceTest {
    
    private final ClientOperationService clientOperationService = new MockClientOperationService();
    
    @Test
    void testSubscribeAndUnsubscribeDefaultMethods() {
        Service service = Service.newService("namespace", "group", "service");
        Subscriber subscriber = new Subscriber();
        
        assertDoesNotThrow(
            () -> clientOperationService.subscribeService(service, subscriber, "client"));
        assertDoesNotThrow(
            () -> clientOperationService.unsubscribeService(service, subscriber, "client"));
    }
    
    @Test
    void testGetPublishInfoWithCustomAttributesAndDefaultCluster() {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        instance.setMetadata(Collections.singletonMap("k", "v"));
        instance.setInstanceId("instanceId");
        instance.setWeight(2.0D);
        instance.setEnabled(false);
        instance.setHealthy(true);
        
        InstancePublishInfo actual = clientOperationService.getPublishInfo(instance);
        
        assertEquals("1.1.1.1", actual.getIp());
        assertEquals(8848, actual.getPort());
        assertTrue(actual.isHealthy());
        assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, actual.getCluster());
        assertEquals("v", actual.getExtendDatum().get("k"));
        assertEquals("instanceId", actual.getExtendDatum().get(CUSTOM_INSTANCE_ID));
        assertEquals(2.0D, actual.getExtendDatum().get(PUBLISH_INSTANCE_WEIGHT));
        assertFalse((Boolean) actual.getExtendDatum().get(PUBLISH_INSTANCE_ENABLE));
    }
    
    private static class MockClientOperationService implements ClientOperationService {
        
        @Override
        public void registerInstance(Service service, Instance instance, String clientId)
            throws NacosException {
        }
        
        @Override
        public void batchRegisterInstance(Service service, List<Instance> instances,
            String clientId) {
        }
        
        @Override
        public void deregisterInstance(Service service, Instance instance, String clientId) {
        }
    }
}
