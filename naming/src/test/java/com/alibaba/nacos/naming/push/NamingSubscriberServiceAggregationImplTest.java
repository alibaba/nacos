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

package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NamingSubscriberServiceAggregationImplTest {
    
    private final String namespace = "N";
    
    private final String serviceName = "G@@S";
    
    private final Service service = Service.newService(namespace, "G", "S");
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private NamingSubscriberServiceLocalImpl local;
    
    @Mock
    private ConfigurableEnvironment environment;
    
    private HashMap<String, Member> members;
    
    private NamingSubscriberServiceAggregationImpl aggregation;
    
    @Before
    public void setUp() throws Exception {
        aggregation = new NamingSubscriberServiceAggregationImpl(local, memberManager);
        Subscriber subscriber = new Subscriber("local", "", "", "", namespace, serviceName, 0);
        when(local.getSubscribers(namespace, serviceName)).thenReturn(Collections.singletonList(subscriber));
        when(local.getSubscribers(service)).thenReturn(Collections.singletonList(subscriber));
        when(local.getFuzzySubscribers(namespace, serviceName)).thenReturn(Collections.singletonList(subscriber));
        when(local.getFuzzySubscribers(service)).thenReturn(Collections.singletonList(subscriber));
        members = new HashMap<>();
        members.put("1", Mockito.mock(Member.class));
        when(memberManager.getServerList()).thenReturn(members);
    }
    
    @Test
    public void testGetSubscribersByStringWithLocal() {
        Collection<Subscriber> actual = aggregation.getSubscribers(namespace, serviceName);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    public void testGetSubscribersByStringWithRemote() {
        // TODO
    }
    
    @Test
    public void testGetSubscribersByServiceWithLocal() {
        Collection<Subscriber> actual = aggregation.getSubscribers(service);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    public void testGetSubscribersByServiceWithRemote() {
        // TODO
    }
    
    @Test
    public void testGetFuzzySubscribersByStringWithLocal() {
        Collection<Subscriber> actual = aggregation.getFuzzySubscribers(namespace, serviceName);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    public void testGetFuzzySubscribersByServiceWithLocal() {
        Collection<Subscriber> actual = aggregation.getFuzzySubscribers(service);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
}
