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

package com.alibaba.nacos.naming.push.v2;

import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.task.PushDelayTask;
import com.alibaba.nacos.naming.push.v2.task.PushDelayTaskExecuteEngine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NamingSubscriberServiceV2ImplTest {
    
    private final String testClientId = "testClientId";
    
    private final Service service = Service.newService("N", "G", "S");
    
    private final Service service1 = Service.newService("N", "G1", "S1");
    
    @Mock
    private ClientManagerDelegate clientManager;
    
    @Mock
    private ClientServiceIndexesManager indexesManager;
    
    @Mock
    private PushDelayTaskExecuteEngine delayTaskEngine;
    
    @Mock
    private Client client;
    
    @Mock
    private UpgradeJudgement upgradeJudgement;
    
    @Mock
    private SwitchDomain switchDomain;
    
    private NamingSubscriberServiceV2Impl subscriberService;
    
    @Before
    public void setUp() throws Exception {
        subscriberService = new NamingSubscriberServiceV2Impl(clientManager, indexesManager, null, null,
                upgradeJudgement, switchDomain);
        ReflectionTestUtils.setField(subscriberService, "delayTaskEngine", delayTaskEngine);
        when(indexesManager.getAllClientsSubscribeService(service)).thenReturn(Collections.singletonList(testClientId));
        when(indexesManager.getAllClientsSubscribeService(service1))
                .thenReturn(Collections.singletonList(testClientId));
        Collection<Service> services = new LinkedList<>();
        services.add(service);
        services.add(service1);
        when(indexesManager.getSubscribedService()).thenReturn(services);
        when(clientManager.getClient(testClientId)).thenReturn(client);
        when(client.getSubscriber(service)).thenReturn(
                new Subscriber("1.1.1.1:1111", "Test", "unknown", "1.1.1.1", "N", service.getGroupedServiceName(), 0));
        when(client.getSubscriber(service1)).thenReturn(
                new Subscriber("1.1.1.1:1111", "Test", "unknown", "1.1.1.1", "N", service1.getGroupedServiceName(), 0));
        when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(true);
    }
    
    @Test
    public void testGetSubscribersByString() {
        Collection<Subscriber> actual = subscriberService
                .getSubscribers(service.getNamespace(), service.getGroupedServiceName());
        assertEquals(1, actual.size());
        assertEquals(service.getGroupedServiceName(), actual.iterator().next().getServiceName());
    }
    
    @Test
    public void testGetSubscribersByService() {
        Collection<Subscriber> actual = subscriberService.getSubscribers(service);
        assertEquals(1, actual.size());
        assertEquals(service.getGroupedServiceName(), actual.iterator().next().getServiceName());
    }
    
    @Test
    public void testGetFuzzySubscribersByString() {
        Collection<Subscriber> actual = subscriberService
                .getFuzzySubscribers(service.getNamespace(), service.getGroupedServiceName());
        assertEquals(2, actual.size());
    }
    
    @Test
    public void testGetFuzzySubscribersByService() {
        Collection<Subscriber> actual = subscriberService.getFuzzySubscribers(service);
        assertEquals(2, actual.size());
    }
    
    @Test
    public void onEvent() {
        subscriberService.onEvent(new ServiceEvent.ServiceChangedEvent(service));
        verify(delayTaskEngine).addTask(eq(service), any(PushDelayTask.class));
    }
}
