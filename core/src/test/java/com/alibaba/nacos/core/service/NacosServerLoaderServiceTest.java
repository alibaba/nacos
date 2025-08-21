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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        nacosServerLoaderService = new NacosServerLoaderService(connectionManager, serverMemberManager,
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
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(member));
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
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(member));
        
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
}