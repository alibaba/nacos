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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class NamingSubscriberServiceAggregationImplTest {
    
    private final String namespace = "N";
    
    private final String serviceName = "G@@S";
    
    private final Service service = Service.newService(namespace, "G", "S");
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private NamingSubscriberServiceLocalImpl local;
    
    private HashMap<String, Member> members;
    
    private NamingSubscriberServiceAggregationImpl aggregation;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        EnvUtil.setContextPath("/nacos");
        aggregation = new NamingSubscriberServiceAggregationImpl(local, memberManager);
        Subscriber subscriber = new Subscriber("local", "", "", "", namespace, serviceName, 0);
        when(local.getSubscribers(namespace, serviceName))
            .thenReturn(Collections.singletonList(subscriber));
        when(local.getSubscribers(service)).thenReturn(Collections.singletonList(subscriber));
        when(local.getFuzzySubscribers(namespace, serviceName))
            .thenReturn(Collections.singletonList(subscriber));
        when(local.getFuzzySubscribers(service)).thenReturn(Collections.singletonList(subscriber));
        members = new HashMap<>();
        members.put("1", Mockito.mock(Member.class));
        when(memberManager.getServerList()).thenReturn(members);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
        EnvUtil.setContextPath(null);
    }
    
    @Test
    void testGetSubscribersByStringWithLocal() {
        Collection<Subscriber> actual = aggregation.getSubscribers(namespace, serviceName);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    void testGetSubscribersByStringWithRemote() {
        mockRemoteCluster();
        try (MockedStatic<HttpClient> httpClient = Mockito.mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.httpGet(anyString(), anyList(), anyMap()))
                .thenReturn(successRemoteResponse());
            
            Collection<Subscriber> actual = aggregation.getSubscribers(namespace, serviceName);
            
            assertSubscribersWithRemote(actual);
        }
    }
    
    @Test
    void testGetSubscribersByServiceWithLocal() {
        Collection<Subscriber> actual = aggregation.getSubscribers(service);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    void testGetSubscribersByServiceWithRemote() {
        mockRemoteCluster();
        try (MockedStatic<HttpClient> httpClient = Mockito.mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.httpGet(anyString(), anyList(), anyMap()))
                .thenReturn(successRemoteResponse());
            
            Collection<Subscriber> actual = aggregation.getSubscribers(service);
            
            assertSubscribersWithRemote(actual);
        }
    }
    
    @Test
    void testGetFuzzySubscribersByStringWithLocal() {
        Collection<Subscriber> actual = aggregation.getFuzzySubscribers(namespace, serviceName);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    void testGetFuzzySubscribersByServiceWithLocal() {
        Collection<Subscriber> actual = aggregation.getFuzzySubscribers(service);
        assertEquals(1, actual.size());
        assertEquals("local", actual.iterator().next().getAddrStr());
    }
    
    @Test
    void testGetFuzzySubscribersByStringWithRemoteFailure() {
        mockRemoteCluster();
        try (MockedStatic<HttpClient> httpClient = Mockito.mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.httpGet(anyString(), anyList(), anyMap()))
                .thenReturn(new RestResult<>(500, "error", ""));
            
            Collection<Subscriber> actual = aggregation.getFuzzySubscribers(namespace, serviceName);
            
            assertEquals(1, actual.size());
            assertEquals("local", actual.iterator().next().getAddrStr());
        }
    }
    
    @Test
    void testGetFuzzySubscribersByServiceWithRemoteFailure() {
        mockRemoteCluster();
        try (MockedStatic<HttpClient> httpClient = Mockito.mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.httpGet(anyString(), anyList(), anyMap()))
                .thenReturn(new RestResult<>(500, "error", ""));
            
            Collection<Subscriber> actual = aggregation.getFuzzySubscribers(service);
            
            assertEquals(1, actual.size());
            assertEquals("local", actual.iterator().next().getAddrStr());
        }
    }
    
    private void mockRemoteCluster() {
        Member remoteMember = Mockito.mock(Member.class);
        when(remoteMember.getAddress()).thenReturn("127.0.0.2:8848");
        members.put("2", remoteMember);
        when(memberManager.getServerList()).thenReturn(members);
        when(memberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(remoteMember));
    }
    
    private RestResult<String> successRemoteResponse() {
        SubscriberInfo remoteSubscriber = new SubscriberInfo();
        remoteSubscriber.setNamespaceId(namespace);
        remoteSubscriber.setServiceName("S");
        remoteSubscriber.setGroupName("G");
        remoteSubscriber.setIp("remote");
        remoteSubscriber.setPort(8080);
        remoteSubscriber.setAgent("remote-agent");
        remoteSubscriber.setAppName("remote-app");
        Page<SubscriberInfo> page = new Page<>();
        page.setPageItems(Collections.singletonList(remoteSubscriber));
        return new RestResult<>(200, "success", JacksonUtils.toJson(Result.success(page)));
    }
    
    private void assertSubscribersWithRemote(Collection<Subscriber> actual) {
        assertEquals(2, actual.size());
        Subscriber remoteSubscriber = actual.stream()
            .filter(each -> "remote:8080".equals(each.getAddrStr())).findFirst().orElseThrow();
        assertEquals("remote-agent", remoteSubscriber.getAgent());
        assertEquals("remote-app", remoteSubscriber.getApp());
        assertEquals(namespace, remoteSubscriber.getNamespaceId());
        assertEquals(serviceName, remoteSubscriber.getServiceName());
        assertEquals(8080, remoteSubscriber.getPort());
    }
}
