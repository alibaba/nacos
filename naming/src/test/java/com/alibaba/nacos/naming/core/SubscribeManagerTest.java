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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.NamingSubscriberServiceAggregationImpl;
import com.alibaba.nacos.naming.push.NamingSubscriberServiceLocalImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class SubscribeManagerTest {
    
    private SubscribeManager subscribeManager;
    
    @Mock
    private NamingSubscriberServiceAggregationImpl aggregation;
    
    @Mock
    private NamingSubscriberServiceLocalImpl local;
    
    @BeforeEach
    void before() {
        subscribeManager = new SubscribeManager();
        ReflectionTestUtils.setField(subscribeManager, "aggregationService", aggregation);
        ReflectionTestUtils.setField(subscribeManager, "localService", local);
    }
    
    @Test
    void getSubscribersWithFalse() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.FALSE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId, serviceName, 0);
            clients.add(subscriber);
            Mockito.when(this.local.getFuzzySubscribers(Mockito.anyString(), Mockito.anyString())).thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            assertNotNull(list);
            assertEquals(1, list.size());
            assertEquals("public", list.get(0).getNamespaceId());
        } catch (Exception ignored) {
        
        }
    }
    
    @Test
    void testGetSubscribersFuzzy() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.TRUE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId,
                    "testGroupName@@test_subscriber", 0);
            clients.add(subscriber);
            Mockito.when(this.aggregation.getFuzzySubscribers(Mockito.anyString(), Mockito.anyString())).thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            assertNotNull(list);
            assertEquals(1, list.size());
            assertEquals("testGroupName@@test_subscriber", list.get(0).getServiceName());
        } catch (Exception ignored) {
        
        }
    }
    
    @Test
    void getSubscribersWithTrue() {
        String serviceName = "testGroupName@@test_subscriber";
        String namespaceId = "public";
        boolean aggregation = Boolean.TRUE;
        try {
            List<Subscriber> clients = new ArrayList<>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId, serviceName, 0);
            clients.add(subscriber);
            Mockito.when(this.aggregation.getFuzzySubscribers(Mockito.anyString(), Mockito.anyString())).thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            assertNotNull(list);
            assertEquals(1, list.size());
            assertEquals("testGroupName@@test_subscriber", list.get(0).getServiceName());
            assertEquals("public", list.get(0).getNamespaceId());
        } catch (Exception ignored) {
        
        }
    }
}

