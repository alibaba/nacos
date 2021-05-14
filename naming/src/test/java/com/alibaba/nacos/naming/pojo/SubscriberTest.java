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

package com.alibaba.nacos.naming.pojo;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SubscriberTest {
    
    @Test
    public void subscriberBeanTest() {
        Subscriber subscriber = new Subscriber("127.0.0.1:8080", "agent", "app", "127.0.0.1", "public", "test", 0);
        subscriber.setAddrStr("127.0.0.1:8080");
        subscriber.setIp("127.0.0.1");
        subscriber.setApp("app");
        subscriber.setAgent("agent");
        subscriber.setNamespaceId("public");
        subscriber.setServiceName("test");
        
        subscriber.getAddrStr();
        subscriber.getIp();
        subscriber.getAgent();
        subscriber.getApp();
        subscriber.getNamespaceId();
        subscriber.getServiceName();
        
        Subscribers subscribers = new Subscribers();
        List<Subscriber> subscriberList = new ArrayList<>();
        subscriberList.add(subscriber);
        subscribers.setSubscribers(subscriberList);
        subscribers.getSubscribers();
        
        Assert.assertNotNull(subscriberList);
        Assert.assertEquals(1, subscriberList.size());
        Assert.assertEquals("127.0.0.1:8080", subscriberList.get(0).getAddrStr());
        Assert.assertEquals("127.0.0.1", subscriberList.get(0).getIp());
        Assert.assertEquals("app", subscriberList.get(0).getApp());
        Assert.assertEquals("agent", subscriberList.get(0).getAgent());
        Assert.assertEquals("public", subscriberList.get(0).getNamespaceId());
        Assert.assertEquals("test", subscriberList.get(0).getServiceName());
    }
}
