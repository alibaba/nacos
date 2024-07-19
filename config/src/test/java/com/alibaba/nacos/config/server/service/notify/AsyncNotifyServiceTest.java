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

package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.config.remote.response.cluster.ConfigChangeClusterSyncResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.remote.ConfigClusterRpcClientProxy;
import com.alibaba.nacos.config.server.service.notify.AsyncNotifyService.AsyncRpcNotifyCallBack;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.service.notify.AsyncNotifyService.HEALTHY_CHECK_STATUS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

/**
 * AsyncNotifyServiceTest.
 *
 * @author shiyiyue
 */
@ExtendWith(SpringExtension.class)
class AsyncNotifyServiceTest {
    
    @Mock
    ServerMemberManager serverMemberManager;
    
    MockedStatic<EnvUtil> envUtilMocked;
    
    MockedStatic<ConfigExecutor> configExecutorMocked;
    
    MockedStatic<InetUtils> inetUtilsMocked;
    
    @Mock
    private ConfigClusterRpcClientProxy configClusterRpcClientProxy;
    
    @BeforeEach
    void setUp() {
        envUtilMocked = Mockito.mockStatic(EnvUtil.class);
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
        inetUtilsMocked = Mockito.mockStatic(InetUtils.class);
        inetUtilsMocked.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
    }
    
    @AfterEach
    void after() {
        envUtilMocked.close();
        inetUtilsMocked.close();
        configExecutorMocked.close();
    }
    
    @Test
    void testSyncConfigChangeCallback() {
        long timeStamp = System.currentTimeMillis();
        Member member1 = new Member();
        member1.setIp("testip1" + timeStamp);
        member1.setState(NodeState.UP);
        AsyncNotifyService asyncNotifyService = new AsyncNotifyService(serverMemberManager);
        ReflectionTestUtils.setField(asyncNotifyService, "configClusterRpcClientProxy", configClusterRpcClientProxy);
        String dataId = "testDataId" + timeStamp;
        String group = "testGroup";
        AsyncNotifyService.NotifySingleRpcTask notifySingleRpcTask = new AsyncNotifyService.NotifySingleRpcTask(dataId, group, null, null,
                0, false, false, member1);
        configExecutorMocked.when(() -> ConfigExecutor.scheduleAsyncNotify(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> null);
        
        notifySingleRpcTask.setBatch(true);
        notifySingleRpcTask.setTag("test");
        notifySingleRpcTask.setBeta(false);
        AsyncRpcNotifyCallBack asyncRpcNotifyCallBack = new AsyncRpcNotifyCallBack(asyncNotifyService, notifySingleRpcTask);
        ConfigChangeClusterSyncResponse successResponse = new ConfigChangeClusterSyncResponse();
        //1. success response
        asyncRpcNotifyCallBack.onResponse(successResponse);
        //2. fail response
        successResponse.setResultCode(500);
        asyncRpcNotifyCallBack.onResponse(successResponse);
        //3. exception
        asyncRpcNotifyCallBack.onException(new NacosException());
        
        // expect schedule twice fail or exception response.
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleAsyncNotify(any(AsyncNotifyService.AsyncRpcTask.class), anyLong(), any(TimeUnit.class)),
                times(2));
    }
    
    /**
     * test HandleConfigDataChangeEvent. expect create a AsyncRpcTask and execute in ConfigExecutor.
     */
    @Test
    void testHandleConfigDataChangeEvent() {
        long timeStamp = System.currentTimeMillis();
        
        List<Member> memberList = new ArrayList<>();
        // member1 success
        Member member1 = new Member();
        member1.setIp("testip1" + timeStamp);
        member1.setState(NodeState.UP);
        memberList.add(member1);
        // member2 exception
        Member member2 = new Member();
        member2.setIp("testip2" + timeStamp);
        member2.setState(NodeState.UP);
        memberList.add(member2);
        // member3 unhealth
        Member member3 = new Member();
        member3.setIp("testip3" + timeStamp);
        member3.setState(NodeState.DOWN);
        memberList.add(member3);
        
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(memberList);
        
        configExecutorMocked.when(() -> ConfigExecutor.scheduleAsyncNotify(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> null);
        String dataId = "testDataId" + timeStamp;
        String group = "testGroup";
        AsyncNotifyService asyncNotifyService = new AsyncNotifyService(serverMemberManager);
        asyncNotifyService.handleConfigDataChangeEvent(new ConfigDataChangeEvent(dataId, group, System.currentTimeMillis()));
        
        // expect schedule twice fail or exception response.
        configExecutorMocked.verify(() -> ConfigExecutor.executeAsyncNotify(any(AsyncNotifyService.AsyncRpcTask.class)), times(1));
        
    }
    
    @Test
    void testExecuteAsyncRpcTask() throws Exception {
        long timeStamp = System.currentTimeMillis();
        String dataId = "testDataId" + timeStamp;
        String group = "testGroup";
        
        List<Member> memberList = new ArrayList<>();
        // member1 success
        Member member1 = new Member();
        member1.setIp("testip1" + timeStamp);
        member1.setState(NodeState.UP);
        memberList.add(member1);
        // member2 exception
        Member member2 = new Member();
        member2.setIp("testip2" + timeStamp);
        member2.setState(NodeState.UP);
        memberList.add(member2);
        // member3 unhealth
        Member member3 = new Member();
        member3.setIp("testip3" + timeStamp);
        member3.setState(NodeState.DOWN);
        memberList.add(member3);
        Queue<AsyncNotifyService.NotifySingleRpcTask> rpcQueue = new LinkedList<>();
        
        for (Member member : memberList) {
            // grpc report data change only
            rpcQueue.add(new AsyncNotifyService.NotifySingleRpcTask(dataId, group, null, null, System.currentTimeMillis(), false, false,
                    member));
        }
        
        AsyncNotifyService asyncNotifyService = new AsyncNotifyService(serverMemberManager);
        
        ReflectionTestUtils.setField(asyncNotifyService, "configClusterRpcClientProxy", configClusterRpcClientProxy);
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(memberList);
        Mockito.when(serverMemberManager.hasMember(eq(member1.getAddress()))).thenReturn(true);
        Mockito.when(serverMemberManager.hasMember(eq(member2.getAddress()))).thenReturn(true);
        Mockito.when(serverMemberManager.hasMember(eq(member3.getAddress()))).thenReturn(true);
        Mockito.when(serverMemberManager.stateCheck(eq(member1.getAddress()), eq(HEALTHY_CHECK_STATUS))).thenReturn(true);
        Mockito.when(serverMemberManager.stateCheck(eq(member2.getAddress()), eq(HEALTHY_CHECK_STATUS))).thenReturn(true);
        // mock stateCheck fail before notify member3
        Mockito.when(serverMemberManager.stateCheck(eq(member3.getAddress()), eq(HEALTHY_CHECK_STATUS))).thenReturn(false);
        //mock syncConfigChange exception when notify member2
        Mockito.doThrow(new NacosException()).when(configClusterRpcClientProxy)
                .syncConfigChange(eq(member2), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        configExecutorMocked.when(() -> ConfigExecutor.scheduleAsyncNotify(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> null);
        
        asyncNotifyService.executeAsyncRpcTask(rpcQueue);
        
        Mockito.verify(configClusterRpcClientProxy, times(1))
                .syncConfigChange(eq(member1), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        Mockito.verify(configClusterRpcClientProxy, times(1))
                .syncConfigChange(eq(member2), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        Mockito.verify(configClusterRpcClientProxy, times(0))
                .syncConfigChange(eq(member3), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        
        //verify scheduleAsyncNotify member2 & member3 in task when syncConfigChange fail
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleAsyncNotify(any(AsyncNotifyService.AsyncRpcTask.class), anyLong(), any(TimeUnit.class)),
                times(2));
        
    }
}
