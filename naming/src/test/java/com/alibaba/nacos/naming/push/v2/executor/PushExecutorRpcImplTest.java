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

package com.alibaba.nacos.naming.push.v2.executor;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PushExecutorRpcImplTest {
    
    private final String rpcClientId = UUID.randomUUID().toString();
    
    @Mock
    private RpcPushService pushService;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private PushCallBack pushCallBack;
    
    private PushDataWrapper pushData;
    
    private PushExecutorRpcImpl pushExecutor;
    
    @Before
    public void setUp() throws Exception {
        pushData = new PushDataWrapper(new ServiceInfo("G@@S"));
        pushExecutor = new PushExecutorRpcImpl(pushService);
        doAnswer(new CallbackAnswer()).when(pushService)
                .pushWithCallback(eq(rpcClientId), any(NotifySubscriberRequest.class), eq(pushCallBack),
                        eq(GlobalExecutor.getCallbackExecutor()));
    }
    
    @Test
    public void testDoPush() {
        pushExecutor.doPush(rpcClientId, subscriber, pushData);
        verify(pushService).pushWithoutAck(eq(rpcClientId), any(NotifySubscriberRequest.class));
    }
    
    @Test
    public void testDoPushWithCallback() {
        pushExecutor.doPushWithCallback(rpcClientId, subscriber, pushData, pushCallBack);
        verify(pushCallBack).onSuccess();
    }
    
    private class CallbackAnswer implements Answer<Void> {
        
        @Override
        public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
            NotifySubscriberRequest pushRequest = invocationOnMock.getArgument(1);
            assertEquals(pushData.getOriginalData(), pushRequest.getServiceInfo());
            PushCallBack callBack = invocationOnMock.getArgument(2);
            callBack.onSuccess();
            return null;
        }
    }
}
