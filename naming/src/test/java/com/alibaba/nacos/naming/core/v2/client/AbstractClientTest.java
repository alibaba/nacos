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
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;

/**
 * AbstractClient test.
 * @ClassName: AbstractClientTest
 * @Author: ChenHao26
 * @Date: 2022/8/3 16:22
 * @Description: TODO
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractClientTest {
    
    private AbstractClient abstractClient;
    
    private Service service;
    
    private InstancePublishInfo instancePublishInfo;
    
    private Subscriber subscriber;
    
    @Before
    public void setUp() {
        abstractClient = new MockAbstractClient();
        service = Service.newService("ns1", "group1", "serviceName001");
        instancePublishInfo = new InstancePublishInfo("127.0.0.1", 8890);
        subscriber = new Subscriber("127.0.0.1:8848", "agent1", "appName", "127.0.0.1",
                "ns1", "serviceName001", 9090);
        addServiceInstance();
        addServiceSubscriber();
    }
    
    private void addServiceInstance() {
        boolean result = abstractClient.addServiceInstance(service, instancePublishInfo);
        Assert.assertTrue(result);
    }
    
    private void  addServiceSubscriber() {
        Assert.assertTrue(abstractClient.addServiceSubscriber(service, subscriber));
    }
    
    @Test
    public void testGetLastUpdatedTime() {
        Assert.assertNotNull(abstractClient.getLastUpdatedTime());
    }
    
    @Test
    public void removeServiceInstanceSuccess() {
        InstancePublishInfo publishInfo = abstractClient.removeServiceInstance(service);
        Assert.assertNotNull(publishInfo);
    }
    
    @Test
    public void getInstancePublishInfo() {
        InstancePublishInfo publishInfo = abstractClient.getInstancePublishInfo(service);
        Assert.assertNotNull(publishInfo);
    }
    
    @Test
    public void getAllPublishedService() {
        Collection<Service> allPublishedService = abstractClient.getAllPublishedService();
        Assert.assertNotNull(allPublishedService);
    }
    
    @Test
    public void removeServiceSubscriber() {
        boolean result = abstractClient.removeServiceSubscriber(service);
        Assert.assertTrue(result);
    }
    
    @Test
    public void getSubscriber() {
        Subscriber subscriber1 = abstractClient.getSubscriber(service);
        Assert.assertNotNull(subscriber1);
    }
    
    @Test
    public void getAllSubscribeService() {
        Collection<Service> allSubscribeService = abstractClient.getAllSubscribeService();
        Assert.assertNotNull(allSubscribeService);
    }
    
    @Test
    public void generateSyncData() {
        ClientSyncData clientSyncData = abstractClient.generateSyncData();
        Assert.assertNotNull(clientSyncData);
    }
    
    @Test
    public void release() {
        try {
            abstractClient.release();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertNotNull(e);
        }
    }
}
