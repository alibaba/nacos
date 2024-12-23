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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.ability.ServerAbilities;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.remote.ability.ServerRemoteAbility;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.model.response.ServerLoaderMetrics;
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
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ServerLoaderControllerV3} unit test.
 *
 * @author yunye
 * @since 3.0.0-bate
 */
@ExtendWith(MockitoExtension.class)
class ServerLoaderControllerV3Test {
    
    @InjectMocks
    private ServerLoaderControllerV3 serverLoaderControllerV3;
    
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
    
    @Test
    void testCurrentClients() {
        Mockito.when(connectionManager.currentClients()).thenReturn(new HashMap<>());
        
        Result<Map<String, Connection>> result = serverLoaderControllerV3.currentClients();
        assertEquals(0, result.getData().size());
    }
    
    @Test
    void testReloadCount() {
        Result<String> result = serverLoaderControllerV3.reloadCount(1, "1.1.1.1");
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
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
        
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        Result<String> result = serverLoaderControllerV3.smartReload(httpServletRequest, "1");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
    }
    
    @Test
    void testReloadSingle() {
        Result<String> result = serverLoaderControllerV3.reloadSingle("111", "1.1.1.1");
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
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
        metrics.put("sdkConCount", "1");
        metrics.put("conCount", "2");
        metrics.put("load", "3");
        metrics.put("cpu", "4");
        ServerLoaderInfoResponse serverLoaderInfoResponse = new ServerLoaderInfoResponse();
        serverLoaderInfoResponse.setLoaderMetrics(metrics);
        Mockito.when(serverLoaderInfoRequestHandler.handle(Mockito.any(), Mockito.any()))
                .thenReturn(serverLoaderInfoResponse);
        
        Mockito.when(serverMemberManager.getSelf()).thenReturn(member);
        
        Result<ServerLoaderMetrics> result = serverLoaderControllerV3.loaderMetrics();
        
        assertEquals(1, result.getData().getDetail().size());
        assertEquals(1, result.getData().getDetail().get(0).getSdkConCount());
        assertEquals(2, result.getData().getDetail().get(0).getConCount());
        assertEquals("3", result.getData().getDetail().get(0).getLoad());
        assertEquals("4", result.getData().getDetail().get(0).getCpu());
        assertEquals("1.1.1.1:8848", result.getData().getDetail().get(0).getAddress());
    }
}
