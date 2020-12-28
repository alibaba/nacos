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

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.NamingSubscriberServiceAggregationImpl;
import com.alibaba.nacos.naming.push.NamingSubscriberServiceLocalImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class SubscribeManagerTest {
    
    private SubscribeManager subscribeManager;
    
    @Mock
    private NamingSubscriberServiceAggregationImpl aggregation;
    
    @Mock
    private NamingSubscriberServiceLocalImpl local;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Before
    public void before() {
        subscribeManager = new SubscribeManager();
        ReflectionTestUtils.setField(subscribeManager, "aggregationService", aggregation);
        ReflectionTestUtils.setField(subscribeManager, "localService", local);
    }
    
    @Test
    public void getSubscribersWithFalse() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.FALSE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId,
                    serviceName, 0);
            clients.add(subscriber);
            Mockito.when(this.local.getFuzzySubscribers(Mockito.anyString(), Mockito.anyString())).thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            Assert.assertNotNull(list);
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("public", list.get(0).getNamespaceId());
        } catch (Exception ignored) {
        
        }
    }
    
    @Test
    public void testGetSubscribersFuzzy() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.TRUE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId,
                    "testGroupName@@test_subscriber", 0);
            clients.add(subscriber);
            Mockito.when(this.aggregation.getFuzzySubscribers(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            Assert.assertNotNull(list);
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("testGroupName@@test_subscriber", list.get(0).getServiceName());
        } catch (Exception ignored) {
        
        }
    }
    
    @Test
    public void getSubscribersWithTrue() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.TRUE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId,
                    serviceName, 0);
            clients.add(subscriber);
            
            List<Member> healthyServers = new ArrayList<>();
            
            for (int i = 0; i <= 2; i++) {
                Member server = new Member();
                server.setIp("127.0.0.1");
                server.setPort(8080 + i);
                server.setState(NodeState.UP);
                server.setExtendVal(MemberMetaDataConstants.AD_WEIGHT, 10);
                server.setExtendVal(MemberMetaDataConstants.SITE_KEY, "site");
                server.setExtendVal(MemberMetaDataConstants.WEIGHT, 1);
                server.setExtendVal(MemberMetaDataConstants.RAFT_PORT, 8000 + i);
                healthyServers.add(server);
            }
            
            Mockito.when(memberManager.allMembers()).thenReturn(healthyServers);
            //Mockito.doReturn(3).when(serverListManager.getHealthyServers().size());
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            Assert.assertNotNull(list);
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("public", list.get(0).getNamespaceId());
        } catch (Exception ignored) {
        
        }
    }
}

