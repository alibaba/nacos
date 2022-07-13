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

package com.alibaba.nacos.core.cluster.remote;

import com.alibaba.nacos.api.ability.ServerAbilities;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.ability.ServerRemoteAbility;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link ClusterRpcClientProxy} unit test.
 *
 * @author chenglu
 * @date 2021-07-08 13:22
 */
@RunWith(MockitoJUnitRunner.class)
public class ClusterRpcClientProxyTest {
    
    @InjectMocks
    private ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private RpcClient client;
    
    private Member member;
    
    @Before
    public void setUp() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        member = new Member();
        member.setIp("1.1.1.1");
        ServerAbilities serverAbilities = new ServerAbilities();
        ServerRemoteAbility remoteAbility = new ServerRemoteAbility();
        remoteAbility.setSupportRemoteConnection(true);
        serverAbilities.setRemoteAbility(remoteAbility);
        member.setAbilities(serverAbilities);
        when(serverMemberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(member));
        clusterRpcClientProxy.init();
        Map<String, RpcClient> clientMap = (Map<String, RpcClient>) ReflectionTestUtils
                .getField(RpcClientFactory.class, "CLIENT_MAP");
        clientMap.remove("Cluster-" + member.getAddress()).shutdown();
        clientMap.put("Cluster-" + member.getAddress(), client);
        when(client.getConnectionType()).thenReturn(ConnectionType.GRPC);
    }
    
    @AfterClass
    public static void tearDown() throws NacosException {
        Map<String, RpcClient> clientMap = (Map<String, RpcClient>) ReflectionTestUtils
                .getField(RpcClientFactory.class, "CLIENT_MAP");
        clientMap.remove("Cluster-1.1.1.1:-1").shutdown();
    }
    
    @Test
    public void testSendRequest() {
        try {
            Response response = clusterRpcClientProxy.sendRequest(member, new HealthCheckRequest());
        } catch (NacosException e) {
            Assert.assertEquals(-401, e.getErrCode());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testAsyncRequest() {
        RequestCallBack requestCallBack = new RequestCallBack() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public long getTimeout() {
                return 0;
            }
            
            @Override
            public void onResponse(Response response) {
            
            }
            
            @Override
            public void onException(Throwable e) {
                Assert.assertTrue(e instanceof NacosException);
            }
        };
        
        try {
            clusterRpcClientProxy.asyncRequest(member, new HealthCheckRequest(), requestCallBack);
        } catch (NacosException e) {
            Assert.assertEquals(500, e.getErrCode());
        }
    }
    
    @Test
    public void testSendRequestToAllMembers() {
        try {
            clusterRpcClientProxy.sendRequestToAllMembers(new HealthCheckRequest());
        } catch (NacosException e) {
            Assert.assertEquals(-401, e.getErrCode());
        }
    }
    
    @Test
    public void testOnEvent() {
        try {
            clusterRpcClientProxy.onEvent(MembersChangeEvent.builder().build());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testIsRunningForClientConnected() {
        when(client.isRunning()).thenReturn(true);
        assertTrue(clusterRpcClientProxy.isRunning(member));
    }
    
    @Test
    public void testIsRunningForClientNotConnected() {
        assertFalse(clusterRpcClientProxy.isRunning(member));
    }
    
    @Test
    public void testIsRunningForNonExist() {
        Member member = new Member();
        member.setIp("11.11.11.11");
        assertFalse(clusterRpcClientProxy.isRunning(member));
    }
}
