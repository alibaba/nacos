/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.remote.core.ServerReloaderRequestHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NacosServerLoaderServiceTest {
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    @Mock
    private ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Mock
    private ServerReloaderRequestHandler serverReloaderRequestHandler;
    
    private NacosServerLoaderService nacosServerLoaderService;
    
    @BeforeEach
    void setUp() {
        nacosServerLoaderService = new NacosServerLoaderService(connectionManager,
            serverMemberManager,
            clusterRpcClientProxy, serverReloaderRequestHandler, serverLoaderInfoRequestHandler);
    }
    
    @Test
    void testCurrentClients() {
        Mockito.when(connectionManager.currentClients()).thenReturn(new HashMap<>());
        Map<String, Connection> result = nacosServerLoaderService.getAllClients();
        assertEquals(0, result.size());
    }
    
    @Test
    void testReloadCount() {
        nacosServerLoaderService.reloadCount(1, "1.1.1.1");
        verify(connectionManager).loadCount(1, "1.1.1.1");
    }
    
    @Test
    void testSmartReload() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(member));
        Map<String, String> metrics = new HashMap<>();
        metrics.put("conCount", "1");
        metrics.put("sdkConCount", "1");
        ServerLoaderInfoResponse serverLoaderInfoResponse = new ServerLoaderInfoResponse();
        serverLoaderInfoResponse.setLoaderMetrics(metrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(Mockito.any(), Mockito.any()))
            .thenReturn(serverLoaderInfoResponse);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(member);
        boolean result = nacosServerLoaderService.smartReload(1f);
        assertTrue(result);
    }
    
    @Test
    void testReloadSingle() {
        nacosServerLoaderService.reloadClient("111", "1.1.1.1");
        verify(connectionManager).loadSingle("111", "1.1.1.1");
    }
    
    @Test
    void testLoaderMetrics() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(member));
        
        Map<String, String> metrics = new HashMap<>();
        metrics.put("sdkConCount", "1");
        metrics.put("conCount", "2");
        metrics.put("load", "3");
        metrics.put("cpu", "4");
        ServerLoaderInfoResponse serverLoaderInfoResponse = new ServerLoaderInfoResponse();
        serverLoaderInfoResponse.setLoaderMetrics(metrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(Mockito.any(), Mockito.any()))
            .thenReturn(serverLoaderInfoResponse);
        
        Mockito.when(serverMemberManager.getSelf()).thenReturn(member);
        
        ServerLoaderMetrics result = nacosServerLoaderService.getServerLoaderMetrics();
        
        assertEquals(1, result.getDetail().size());
        assertEquals(1, result.getDetail().get(0).getSdkConCount());
        assertEquals(2, result.getDetail().get(0).getConCount());
        assertEquals("3", result.getDetail().get(0).getLoad());
        assertEquals("4", result.getDetail().get(0).getCpu());
        assertEquals("1.1.1.1:8848", result.getDetail().get(0).getAddress());
    }
    
    @Test
    void testGetServerLoaderMetricsWhenAsyncRequestThrows() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        doThrow(new NacosException(500, "rpc error")).when(clusterRpcClientProxy)
            .asyncRequest(eq(other), any(), any());
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("sdkConCount", "5");
        selfMetrics.put("conCount", "5");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        
        ServerLoaderMetrics result = nacosServerLoaderService.getServerLoaderMetrics();
        
        assertNotNull(result);
        assertEquals(1, result.getDetail().size());
        assertEquals("1.1.1.1:8848", result.getDetail().get(0).getAddress());
    }
    
    @Test
    void testGetServerLoaderMetricsWhenSelfHandleThrows() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.emptyList());
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any()))
            .thenThrow(new NacosException(500, "self metrics fail"));
        
        ServerLoaderMetrics result = nacosServerLoaderService.getServerLoaderMetrics();
        
        assertNotNull(result);
        assertEquals(1, result.getDetail().size());
        assertEquals("1.1.1.1:8848", result.getDetail().get(0).getAddress());
    }
    
    @Test
    void testGetServerLoaderMetricsCallbackOnException() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        
        ArgumentCaptor<com.alibaba.nacos.api.remote.RequestCallBack> callbackCaptor = ArgumentCaptor
            .forClass(com.alibaba.nacos.api.remote.RequestCallBack.class);
        doAnswer(invocation -> {
            callbackCaptor.getValue().onException(new RuntimeException("remote error"));
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(other), any(), callbackCaptor.capture());
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("sdkConCount", "1");
        selfMetrics.put("conCount", "1");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        
        ServerLoaderMetrics result = nacosServerLoaderService.getServerLoaderMetrics();
        
        assertNotNull(result);
        assertEquals(1, result.getDetail().size());
    }
    
    @Test
    void testGetServerLoaderMetricsCallbackOnResponseNonLoaderResponse() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        
        ArgumentCaptor<com.alibaba.nacos.api.remote.RequestCallBack> callbackCaptor = ArgumentCaptor
            .forClass(com.alibaba.nacos.api.remote.RequestCallBack.class);
        doAnswer(invocation -> {
            callbackCaptor.getValue().onResponse(ErrorResponse.build(0, "non-loader"));
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(other), any(), callbackCaptor.capture());
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("sdkConCount", "1");
        selfMetrics.put("conCount", "1");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        
        ServerLoaderMetrics result = nacosServerLoaderService.getServerLoaderMetrics();
        
        assertNotNull(result);
        assertEquals(1, result.getDetail().size());
    }
    
    @Test
    void testSmartReloadWithRemoteMemberFailure() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        Mockito.when(serverMemberManager.find("2.2.2.2:8848")).thenReturn(other);
        Mockito.when(serverMemberManager.allMembers())
            .thenReturn(java.util.Arrays.asList(self, other));
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("conCount", "1");
        selfMetrics.put("sdkConCount", "1");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        
        Map<String, String> otherMetrics = new HashMap<>();
        otherMetrics.put("conCount", "100");
        otherMetrics.put("sdkConCount", "100");
        ServerLoaderInfoResponse otherResponse = new ServerLoaderInfoResponse();
        otherResponse.setLoaderMetrics(otherMetrics);
        
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        java.util.concurrent.atomic.AtomicInteger asyncCallCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            com.alibaba.nacos.api.remote.RequestCallBack<Response> cb = invocation.getArgument(2);
            if (asyncCallCount.incrementAndGet() == 1) {
                cb.onResponse(otherResponse);
            } else {
                cb.onResponse(ErrorResponse.build(500, "reload fail"));
            }
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(other), any(), any());
        
        boolean result = nacosServerLoaderService.smartReload(0.1f);
        
        assertFalse(result);
    }
    
    @Test
    void testSmartReloadSelfHandleThrows() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        Mockito.when(serverMemberManager.find("1.1.1.1:8848")).thenReturn(self);
        Mockito.when(serverMemberManager.allMembers())
            .thenReturn(java.util.Arrays.asList(self, other));
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("conCount", "100");
        selfMetrics.put("sdkConCount", "100");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        Map<String, String> otherMetrics = new HashMap<>();
        otherMetrics.put("conCount", "1");
        otherMetrics.put("sdkConCount", "1");
        ServerLoaderInfoResponse otherResponse = new ServerLoaderInfoResponse();
        otherResponse.setLoaderMetrics(otherMetrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        doAnswer(invocation -> {
            invocation.getArgument(2, com.alibaba.nacos.api.remote.RequestCallBack.class)
                .onResponse(otherResponse);
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(other), any(), any());
        Mockito.when(serverReloaderRequestHandler.handle(any(), any()))
            .thenThrow(new NacosException(500, "reload fail"));
        
        boolean result = nacosServerLoaderService.smartReload(0.1f);
        
        assertFalse(result);
    }
    
    @Test
    void testSmartReloadRemoteAsyncThrows() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        Mockito.when(serverMemberManager.find("2.2.2.2:8848")).thenReturn(other);
        Mockito.when(serverMemberManager.allMembers())
            .thenReturn(java.util.Arrays.asList(self, other));
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("conCount", "1");
        selfMetrics.put("sdkConCount", "1");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        Map<String, String> otherMetrics = new HashMap<>();
        otherMetrics.put("conCount", "100");
        otherMetrics.put("sdkConCount", "100");
        ServerLoaderInfoResponse otherResponse = new ServerLoaderInfoResponse();
        otherResponse.setLoaderMetrics(otherMetrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        java.util.concurrent.atomic.AtomicInteger asyncCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            if (asyncCount.incrementAndGet() == 1) {
                invocation.getArgument(2, com.alibaba.nacos.api.remote.RequestCallBack.class)
                    .onResponse(otherResponse);
            } else {
                throw new NacosException(500, "async request fail");
            }
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(other), any(), any());
        
        boolean result = nacosServerLoaderService.smartReload(0.1f);
        
        assertFalse(result);
    }
    
    @Test
    void testSmartReloadRemoteCallbackOnException() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member self = new Member();
        self.setIp("1.1.1.1");
        self.setPort(8848);
        Member other = new Member();
        other.setIp("2.2.2.2");
        other.setPort(8848);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        Mockito.when(serverMemberManager.find("2.2.2.2:8848")).thenReturn(other);
        Mockito.when(serverMemberManager.allMembers())
            .thenReturn(java.util.Arrays.asList(self, other));
        Mockito.when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(other));
        
        Map<String, String> selfMetrics = new HashMap<>();
        selfMetrics.put("conCount", "1");
        selfMetrics.put("sdkConCount", "1");
        ServerLoaderInfoResponse selfResponse = new ServerLoaderInfoResponse();
        selfResponse.setLoaderMetrics(selfMetrics);
        Map<String, String> otherMetrics = new HashMap<>();
        otherMetrics.put("conCount", "100");
        otherMetrics.put("sdkConCount", "100");
        ServerLoaderInfoResponse otherResponse = new ServerLoaderInfoResponse();
        otherResponse.setLoaderMetrics(otherMetrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(any(), any())).thenReturn(selfResponse);
        java.util.concurrent.atomic.AtomicInteger asyncCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            com.alibaba.nacos.api.remote.RequestCallBack<Response> cb = invocation.getArgument(2);
            if (asyncCount.incrementAndGet() == 1) {
                cb.onResponse(otherResponse);
            } else {
                cb.onException(new RuntimeException("remote reload exception"));
            }
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(other), any(), any());
        
        boolean result = nacosServerLoaderService.smartReload(0.1f);
        
        assertFalse(result);
    }
}
