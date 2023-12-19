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
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.remote.ConfigClusterRpcClientProxy;
import com.alibaba.nacos.config.server.service.notify.AsyncNotifyService.AsyncRpcNotifyCallBack;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.service.notify.AsyncNotifyService.HEALTHY_CHECK_STATUS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

/**
 * @author shiyiyue
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class AsyncNotifyServiceTest {
    
    @Mock
    ServerMemberManager serverMemberManager;
    
    @Mock
    private ConfigClusterRpcClientProxy configClusterRpcClientProxy;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ConfigExecutor> configExecutorMockedStatic;
    
    MockedStatic<InetUtils> inetUtilsMockedStatic;
    
    @Before
    public void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        configExecutorMockedStatic = Mockito.mockStatic(ConfigExecutor.class);
        inetUtilsMockedStatic = Mockito.mockStatic(InetUtils.class);
        inetUtilsMockedStatic.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
    }
    
    @After
    public void after() {
        envUtilMockedStatic.close();
        inetUtilsMockedStatic.close();
        configExecutorMockedStatic.close();
    }
    
    @Test
    public void testSyncConfigChangeCallback() throws Exception {
        long timeStamp = System.currentTimeMillis();
        String dataId = "testDataId" + timeStamp;
        String group = "testGroup";
        Member member1 = new Member();
        member1.setIp("testip1" + timeStamp);
        member1.setState(NodeState.UP);
        AsyncNotifyService asyncNotifyService = new AsyncNotifyService(serverMemberManager);
        ReflectionTestUtils.setField(asyncNotifyService, "configClusterRpcClientProxy", configClusterRpcClientProxy);
        AsyncNotifyService.NotifySingleRpcTask notifySingleRpcTask = new AsyncNotifyService.NotifySingleRpcTask(dataId,
                group, null, null, 0, false, false, member1);
        notifySingleRpcTask.setBatch(true);
        notifySingleRpcTask.setTag("test");
        notifySingleRpcTask.setBeta(false);
        AsyncRpcNotifyCallBack asyncRpcNotifyCallBack = new AsyncRpcNotifyCallBack(asyncNotifyService,
                notifySingleRpcTask);
        ConfigChangeClusterSyncResponse successResponse = new ConfigChangeClusterSyncResponse();
        //1. success response
        asyncRpcNotifyCallBack.onResponse(successResponse);
        //2. fail response
        successResponse.setResultCode(500);
        asyncRpcNotifyCallBack.onResponse(successResponse);
        //3. exception
        asyncRpcNotifyCallBack.onException(new NacosException());
        
    }
    
    @Test
    public void test() throws Exception {
        long timeStamp = System.currentTimeMillis();
        String dataId = "testDataId" + timeStamp;
        String group = "testGroup";
        
        AsyncNotifyService asyncNotifyService = new AsyncNotifyService(serverMemberManager);
        //wait event handler to set up.
        Thread.sleep(1000L);
        
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
        ReflectionTestUtils.setField(asyncNotifyService, "configClusterRpcClientProxy", configClusterRpcClientProxy);
        Mockito.when(serverMemberManager.allMembersWithoutSelf()).thenReturn(memberList);
        Mockito.when(serverMemberManager.hasMember(eq(member1.getAddress()))).thenReturn(true);
        Mockito.when(serverMemberManager.hasMember(eq(member2.getAddress()))).thenReturn(true);
        Mockito.when(serverMemberManager.hasMember(eq(member3.getAddress()))).thenReturn(true);
        Mockito.when(serverMemberManager.stateCheck(eq(member1.getAddress()), eq(HEALTHY_CHECK_STATUS)))
                .thenReturn(true);
        Mockito.when(serverMemberManager.stateCheck(eq(member2.getAddress()), eq(HEALTHY_CHECK_STATUS)))
                .thenReturn(true);
        Mockito.when(serverMemberManager.stateCheck(eq(member3.getAddress()), eq(HEALTHY_CHECK_STATUS)))
                .thenReturn(false);
        Mockito.doThrow(new NacosException()).when(configClusterRpcClientProxy).syncConfigChange(eq(member2), any(ConfigChangeClusterSyncRequest.class),
                any(RequestCallBack.class));
        configExecutorMockedStatic.when(()->ConfigExecutor.scheduleAsyncNotify(any(Runnable.class), anyLong(),
                any(TimeUnit.class))).thenAnswer(invocation->null);
    
        //when ConfigDataChangeEvent publish ,configChangeSubscriber onEvent method will be called.
        NotifyCenter.publishEvent(new ConfigDataChangeEvent(dataId, group, System.currentTimeMillis()));
        Thread.sleep(200L);
        Mockito.verify(configClusterRpcClientProxy, times(1))
                .syncConfigChange(eq(member1), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        Mockito.verify(configClusterRpcClientProxy, times(1))
                .syncConfigChange(eq(member2), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        Mockito.verify(configClusterRpcClientProxy, times(0))
                .syncConfigChange(eq(member3), any(ConfigChangeClusterSyncRequest.class), any(RequestCallBack.class));
        
        /*configExecutorMockedStatic.verify(
                () -> ConfigExecutor.scheduleAsyncNotify(any(AsyncNotifyService.AsyncRpcTask.class), anyLong(),
                        any(TimeUnit.class)), times(1));*/
    }
}
