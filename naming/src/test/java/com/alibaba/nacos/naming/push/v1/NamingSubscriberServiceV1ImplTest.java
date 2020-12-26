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

package com.alibaba.nacos.naming.push.v1;

import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class NamingSubscriberServiceV1ImplTest {
    
    private static NamingSubscriberServiceV1Impl namingSubscriberService;
    
    @BeforeClass
    public static void setUp() throws Exception {
        namingSubscriberService = new NamingSubscriberServiceV1Impl();
        InetSocketAddress socketAddress = new InetSocketAddress(8848);
        PushClient pushClient = new PushClient("1", "G1@@S1", "", "", socketAddress, null, "", "");
        namingSubscriberService.addClient(pushClient);
        namingSubscriberService.addClient("1", "DEFAULT_GROUP@@S2", "", "", socketAddress, null, "", "");
    }
    
    @Test
    public void testGetSubscribersWithStringParam() {
        Collection<Subscriber> actual = namingSubscriberService.getSubscribers("1", "G1@@S1");
        assertEquals(1, actual.size());
        assertSubscriber("1", "G1@@S1", actual.iterator().next());
    }
    
    @Test
    public void testGetSubscribersWithServiceParam() {
        Service service = Service.newService("1", "G1", "S1");
        Collection<Subscriber> actual = namingSubscriberService.getSubscribers(service);
        assertEquals(1, actual.size());
        assertSubscriber("1", "G1@@S1", actual.iterator().next());
    }
    
    @Test
    public void testGetFuzzySubscribersWithStringParam() {
        Collection<Subscriber> actual = namingSubscriberService.getFuzzySubscribers("1", "D@@S");
        assertEquals(1, actual.size());
        assertSubscriber("1", "DEFAULT_GROUP@@S2", actual.iterator().next());
        actual = namingSubscriberService.getFuzzySubscribers("1", "G@@S");
        assertEquals(2, actual.size());
        assertSubscriber("1", "DEFAULT_GROUP@@S2", actual.iterator().next());
        actual = namingSubscriberService.getFuzzySubscribers("1", "X@@S");
        assertEquals(0, actual.size());
    }
    
    @Test
    public void testGetFuzzySubscribersWithServiceParam() {
        Service service = Service.newService("1", "G", "S");
        Collection<Subscriber> actual = namingSubscriberService.getFuzzySubscribers(service);
        assertEquals(2, actual.size());
        assertSubscriber("1", "DEFAULT_GROUP@@S2", actual.iterator().next());
    }
    
    private void assertSubscriber(String namespaceId, String serviceName, Subscriber actual) {
        assertEquals(namespaceId, actual.getNamespaceId());
        assertEquals(serviceName, actual.getServiceName());
    }
}
