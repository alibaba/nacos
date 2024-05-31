/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.exception.DistroException;
import com.alibaba.nacos.naming.cluster.remote.response.DistroDataResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class DistroClientTransportAgentTest {
    
    @Mock
    ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Mock
    ServerMemberManager memberManager;
    
    @Mock
    ConfigurableApplicationContext context;
    
    @Mock
    DistroCallback distroCallback;
    
    @InjectMocks
    DistroClientTransportAgent transportAgent;
    
    Member member;
    
    Response response;
    
    @BeforeEach
    void setUp() throws Exception {
        ApplicationUtils.injectContext(context);
        EnvUtil.setEnvironment(new MockEnvironment());
        member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        response = new DistroDataResponse();
        when(memberManager.find(member.getAddress())).thenReturn(member);
        when(memberManager.getSelf()).thenReturn(member);
        when(clusterRpcClientProxy.sendRequest(eq(member), any())).thenReturn(response);
        doAnswer(invocationOnMock -> {
            RequestCallBack<Response> callback = invocationOnMock.getArgument(2);
            callback.onResponse(response);
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(member), any(), any());
        // When run all project, the TpsNamingMonitor will be init by other unit test, will throw UnnecessaryStubbingException.
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testSupportCallbackTransport() {
        assertTrue(transportAgent.supportCallbackTransport());
    }
    
    @Test
    void testSyncDataForMemberNonExist() throws NacosException {
        assertTrue(transportAgent.syncData(new DistroData(), member.getAddress()));
        verify(memberManager, never()).find(member.getAddress());
        verify(clusterRpcClientProxy, never()).sendRequest(any(Member.class), any());
    }
    
    @Test
    void testSyncDataForMemberUnhealthy() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        assertFalse(transportAgent.syncData(new DistroData(), member.getAddress()));
        verify(clusterRpcClientProxy, never()).sendRequest(any(Member.class), any());
    }
    
    @Test
    void testSyncDataForMemberDisconnect() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        assertFalse(transportAgent.syncData(new DistroData(), member.getAddress()));
        verify(clusterRpcClientProxy, never()).sendRequest(any(Member.class), any());
    }
    
    @Test
    void testSyncDataFailure() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        response.setErrorInfo(ResponseCode.FAIL.getCode(), "TEST");
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        assertFalse(transportAgent.syncData(new DistroData(), member.getAddress()));
    }
    
    @Test
    void testSyncDataException() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.sendRequest(eq(member), any())).thenThrow(new NacosException());
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        assertFalse(transportAgent.syncData(new DistroData(), member.getAddress()));
    }
    
    @Test
    void testSyncDataSuccess() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        assertTrue(transportAgent.syncData(new DistroData(), member.getAddress()));
    }
    
    @Test
    void testSyncDataWithCallbackForMemberNonExist() throws NacosException {
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onSuccess();
        verify(memberManager, never()).find(member.getAddress());
        verify(clusterRpcClientProxy, never()).asyncRequest(any(Member.class), any(), any());
    }
    
    @Test
    void testSyncDataWithCallbackForMemberUnhealthy() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(null);
        verify(clusterRpcClientProxy, never()).asyncRequest(any(Member.class), any(), any());
    }
    
    @Test
    void testSyncDataWithCallbackForMemberDisconnect() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(null);
        verify(clusterRpcClientProxy, never()).asyncRequest(any(Member.class), any(), any());
    }
    
    @Test
    void testSyncDataWithCallbackFailure() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        response.setErrorInfo(ResponseCode.FAIL.getCode(), "TEST");
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(null);
    }
    
    @Test
    void testSyncDataWithCallbackException() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        doThrow(new NacosException()).when(clusterRpcClientProxy).asyncRequest(eq(member), any(), any());
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(any(NacosException.class));
    }
    
    @Test
    void testSyncDataWithCallbackException2() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        doAnswer(invocationOnMock -> {
            RequestCallBack<Response> callback = invocationOnMock.getArgument(2);
            callback.onException(new NacosException());
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(member), any(), any());
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(any(NacosException.class));
    }
    
    @Test
    void testSyncDataWithCallbackSuccess() throws NacosException {
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        transportAgent.syncData(new DistroData(), member.getAddress(), distroCallback);
        verify(distroCallback).onSuccess();
    }
    
    @Test
    void testSyncVerifyDataForMemberNonExist() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        assertTrue(transportAgent.syncVerifyData(verifyData, member.getAddress()));
        verify(memberManager, never()).find(member.getAddress());
        verify(clusterRpcClientProxy, never()).sendRequest(any(Member.class), any());
    }
    
    @Test
    void testSyncVerifyDataForMemberUnhealthy() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        assertFalse(transportAgent.syncVerifyData(verifyData, member.getAddress()));
        verify(clusterRpcClientProxy, never()).sendRequest(any(Member.class), any());
    }
    
    @Test
    void testSyncVerifyDataForMemberDisconnect() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        assertFalse(transportAgent.syncVerifyData(verifyData, member.getAddress()));
        verify(clusterRpcClientProxy, never()).sendRequest(any(Member.class), any());
    }
    
    @Test
    void testSyncVerifyDataFailure() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        response.setErrorInfo(ResponseCode.FAIL.getCode(), "TEST");
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        assertFalse(transportAgent.syncVerifyData(verifyData, member.getAddress()));
    }
    
    @Test
    void testSyncVerifyDataException() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.sendRequest(eq(member), any())).thenThrow(new NacosException());
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        assertFalse(transportAgent.syncVerifyData(verifyData, member.getAddress()));
    }
    
    @Test
    void testSyncVerifyDataSuccess() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        assertTrue(transportAgent.syncVerifyData(verifyData, member.getAddress()));
    }
    
    @Test
    void testSyncVerifyDataWithCallbackForMemberNonExist() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onSuccess();
        verify(memberManager, never()).find(member.getAddress());
        verify(clusterRpcClientProxy, never()).asyncRequest(any(Member.class), any(), any());
    }
    
    @Test
    void testSyncVerifyDataWithCallbackForMemberUnhealthy() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(null);
        verify(clusterRpcClientProxy, never()).asyncRequest(any(Member.class), any(), any());
    }
    
    @Test
    void testSyncVerifyDataWithCallbackForMemberDisconnect() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(null);
        verify(clusterRpcClientProxy, never()).asyncRequest(any(Member.class), any(), any());
    }
    
    @Test
    void testSyncVerifyDataWithCallbackFailure() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        response.setErrorInfo(ResponseCode.FAIL.getCode(), "TEST");
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(null);
    }
    
    @Test
    void testSyncVerifyDataWithCallbackException() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        doThrow(new NacosException()).when(clusterRpcClientProxy).asyncRequest(eq(member), any(), any());
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(any(NacosException.class));
    }
    
    @Test
    void testSyncVerifyDataWithCallbackException2() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        doAnswer(invocationOnMock -> {
            RequestCallBack<Response> callback = invocationOnMock.getArgument(2);
            callback.onException(new NacosException());
            return null;
        }).when(clusterRpcClientProxy).asyncRequest(eq(member), any(), any());
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onFailed(any(NacosException.class));
    }
    
    @Test
    void testSyncVerifyDataWithCallbackSuccess() throws NacosException {
        DistroData verifyData = new DistroData();
        verifyData.setDistroKey(new DistroKey());
        when(memberManager.hasMember(member.getAddress())).thenReturn(true);
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        transportAgent.syncVerifyData(verifyData, member.getAddress(), distroCallback);
        verify(distroCallback).onSuccess();
    }
    
    @Test
    void testGetDataForMemberNonExist() {
        assertThrows(DistroException.class, () -> {
            transportAgent.getData(new DistroKey(), member.getAddress());
        });
    }
    
    @Test
    void testGetDataForMemberUnhealthy() {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            transportAgent.getData(new DistroKey(), member.getAddress());
        });
    }
    
    @Test
    void testGetDataForMemberDisconnect() {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            member.setState(NodeState.UP);
            transportAgent.getData(new DistroKey(), member.getAddress());
        });
    }
    
    @Test
    void testGetDataException() throws NacosException {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            member.setState(NodeState.UP);
            when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
            when(clusterRpcClientProxy.sendRequest(eq(member), any())).thenThrow(new NacosException());
            transportAgent.getData(new DistroKey(), member.getAddress());
        });
    }
    
    @Test
    void testGetDataFailure() {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            member.setState(NodeState.UP);
            when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
            response.setErrorInfo(ResponseCode.FAIL.getCode(), "TEST");
            transportAgent.getData(new DistroKey(), member.getAddress());
        });
    }
    
    @Test
    void testGetDataSuccess() {
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        transportAgent.getData(new DistroKey(), member.getAddress());
    }
    
    @Test
    void testGetDatumSnapshotForMemberNonExist() {
        assertThrows(DistroException.class, () -> {
            transportAgent.getDatumSnapshot(member.getAddress());
        });
    }
    
    @Test
    void testGetDatumSnapshotForMemberUnhealthy() {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            transportAgent.getDatumSnapshot(member.getAddress());
        });
    }
    
    @Test
    void testGetDatumSnapshotForMemberDisconnect() {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            member.setState(NodeState.UP);
            transportAgent.getDatumSnapshot(member.getAddress());
        });
    }
    
    @Test
    void testGetDatumSnapshotException() throws NacosException {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            member.setState(NodeState.UP);
            when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
            when(clusterRpcClientProxy.sendRequest(eq(member), any(), any(Long.class))).thenThrow(new NacosException());
            transportAgent.getDatumSnapshot(member.getAddress());
        });
    }
    
    @Test
    void testGetDatumSnapshotFailure() throws NacosException {
        assertThrows(DistroException.class, () -> {
            when(memberManager.find(member.getAddress())).thenReturn(member);
            member.setState(NodeState.UP);
            when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
            when(clusterRpcClientProxy.sendRequest(eq(member), any(), any(Long.class))).thenReturn(response);
            response.setErrorInfo(ResponseCode.FAIL.getCode(), "TEST");
            transportAgent.getDatumSnapshot(member.getAddress());
        });
    }
    
    @Test
    void testGetDatumSnapshotSuccess() throws NacosException {
        when(memberManager.find(member.getAddress())).thenReturn(member);
        member.setState(NodeState.UP);
        when(clusterRpcClientProxy.isRunning(member)).thenReturn(true);
        when(clusterRpcClientProxy.sendRequest(eq(member), any(), any(Long.class))).thenReturn(response);
        transportAgent.getDatumSnapshot(member.getAddress());
    }
}
