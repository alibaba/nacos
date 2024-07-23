/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.api.ability.ServerAbilities;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.ability.ServerRemoteAbility;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.remote.core.ServerReloaderRequestHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ServerLoaderController} unit test.
 *
 * @author chenglu
 * @date 2021-07-07 23:10
 */
@ExtendWith(MockitoExtension.class)
class ServerLoaderControllerTest {
    
    @InjectMocks
    private ServerLoaderController serverLoaderController;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Mock
    private ServerReloaderRequestHandler serverReloaderRequestHandler;
    
    @Mock
    private ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    @Test
    void testCurrentClients() {
        Mockito.when(connectionManager.currentClients()).thenReturn(new HashMap<>());
        
        ResponseEntity<Map<String, Connection>> result = serverLoaderController.currentClients();
        assertEquals(0, result.getBody().size());
    }
    
    @Test
    void testReloadCount() {
        ResponseEntity<String> result = serverLoaderController.reloadCount(1, "1.1.1.1");
        assertEquals("success", result.getBody());
    }
    
    @Test
    void testSmartReload() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        ServerAbilities serverAbilities = new ServerAbilities();
        ServerRemoteAbility serverRemoteAbility = new ServerRemoteAbility();
        serverRemoteAbility.setSupportRemoteConnection(true);
        serverAbilities.setRemoteAbility(serverRemoteAbility);
        member.setAbilities(serverAbilities);
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(member));
        
        Map<String, String> metrics = new HashMap<>();
        metrics.put("conCount", "1");
        metrics.put("sdkConCount", "1");
        ServerLoaderInfoResponse serverLoaderInfoResponse = new ServerLoaderInfoResponse();
        serverLoaderInfoResponse.setLoaderMetrics(metrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(Mockito.any(), Mockito.any())).thenReturn(serverLoaderInfoResponse);
        
        Mockito.when(serverMemberManager.getSelf()).thenReturn(member);
        
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        ResponseEntity<String> result = serverLoaderController.smartReload(httpServletRequest, "1", null);
        
        assertEquals("Ok", result.getBody());
        
    }
    
    @Test
    void testReloadSingle() {
        ResponseEntity<String> result = serverLoaderController.reloadSingle("111", "1.1.1.1");
        assertEquals("success", result.getBody());
    }
    
    @Test
    void testLoaderMetrics() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        ServerAbilities serverAbilities = new ServerAbilities();
        ServerRemoteAbility serverRemoteAbility = new ServerRemoteAbility();
        serverRemoteAbility.setSupportRemoteConnection(true);
        serverAbilities.setRemoteAbility(serverRemoteAbility);
        member.setAbilities(serverAbilities);
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(member));
        
        Map<String, String> metrics = new HashMap<>();
        metrics.put("conCount", "1");
        ServerLoaderInfoResponse serverLoaderInfoResponse = new ServerLoaderInfoResponse();
        serverLoaderInfoResponse.setLoaderMetrics(metrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(Mockito.any(), Mockito.any())).thenReturn(serverLoaderInfoResponse);
        
        Mockito.when(serverMemberManager.getSelf()).thenReturn(member);
        
        ResponseEntity<Map<String, Object>> result = serverLoaderController.loaderMetrics();
        assertEquals(9, result.getBody().size());
    }
}
