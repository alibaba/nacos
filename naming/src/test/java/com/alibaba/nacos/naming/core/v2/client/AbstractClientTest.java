/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.client;

import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AbstractClientTest {
    
    private AbstractClient abstractClient;
    
    private Service service;
    
    private InstancePublishInfo instancePublishInfo;
    
    private Subscriber subscriber;
    
    @BeforeEach
    void setUp() {
        abstractClient = new MockAbstractClient(0L);
        service = Service.newService("ns1", "group1", "serviceName001");
        instancePublishInfo = new InstancePublishInfo("127.0.0.1", 8890);
        subscriber = new Subscriber("127.0.0.1:8848", "agent1", "appName", "127.0.0.1", "ns1", "serviceName001", 9090);
        MetricsMonitor.getIpCountMonitor().set(0);
        MetricsMonitor.getSubscriberCount().set(0);
    }
    
    @Test
    void addServiceInstance() {
        boolean result = abstractClient.addServiceInstance(service, instancePublishInfo);
        assertTrue(result);
    }
    
    @Test
    void addServiceSubscriber() {
        assertTrue(abstractClient.addServiceSubscriber(service, subscriber));
    }
    
    @Test
    void testGetLastUpdatedTime() {
        assertNotNull(abstractClient.getLastUpdatedTime());
    }
    
    @Test
    void removeServiceInstanceSuccess() {
        addServiceInstance();
        InstancePublishInfo publishInfo = abstractClient.removeServiceInstance(service);
        assertNotNull(publishInfo);
    }
    
    @Test
    void getInstancePublishInfo() {
        addServiceInstance();
        InstancePublishInfo publishInfo = abstractClient.getInstancePublishInfo(service);
        assertNotNull(publishInfo);
    }
    
    @Test
    void getAllPublishedService() {
        Collection<Service> allPublishedService = abstractClient.getAllPublishedService();
        assertNotNull(allPublishedService);
    }
    
    @Test
    void removeServiceSubscriber() {
        boolean result = abstractClient.removeServiceSubscriber(service);
        assertTrue(result);
    }
    
    @Test
    void getSubscriber() {
        addServiceSubscriber();
        Subscriber subscriber1 = abstractClient.getSubscriber(service);
        assertNotNull(subscriber1);
    }
    
    @Test
    void getAllSubscribeService() {
        Collection<Service> allSubscribeService = abstractClient.getAllSubscribeService();
        assertNotNull(allSubscribeService);
    }
    
    @Test
    void generateSyncData() {
        ClientSyncData clientSyncData = abstractClient.generateSyncData();
        assertNotNull(clientSyncData);
    }
    
    @Test
    void release() {
        
        abstractClient.addServiceInstance(service, instancePublishInfo);
        assertEquals(1, MetricsMonitor.getIpCountMonitor().get());
        abstractClient.addServiceSubscriber(service, subscriber);
        assertEquals(1, MetricsMonitor.getSubscriberCount().get());
        
        abstractClient.release();
        
        assertEquals(0, MetricsMonitor.getSubscriberCount().get());
        assertEquals(0, MetricsMonitor.getIpCountMonitor().get());
    }
}
