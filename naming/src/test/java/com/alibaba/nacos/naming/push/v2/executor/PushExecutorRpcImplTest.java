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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.push.v2.task.NamingPushCallback;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushExecutorRpcImplTest {
    
    private final String rpcClientId = UUID.randomUUID().toString();
    
    @Mock
    private RpcPushService pushService;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private NamingPushCallback pushCallBack;
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    private PushDataWrapper pushData;
    
    private PushExecutorRpcImpl pushExecutor;
    
    private ServiceMetadata serviceMetadata;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new MockEnvironment());
        serviceMetadata = new ServiceMetadata();
        pushData = new PushDataWrapper(serviceMetadata, new ServiceInfo("G@@S"));
        pushExecutor = new PushExecutorRpcImpl(pushService);
        EnvUtil.setEnvironment(new MockEnvironment());
        ApplicationUtils.injectContext(context);
        when(context.getBean(SelectorManager.class)).thenReturn(selectorManager);
        when(selectorManager.select(any(), any(), any())).then(
                (Answer<List<Instance>>) invocationOnMock -> invocationOnMock.getArgument(2));
    }
    
    @Test
    void testDoPush() {
        pushExecutor.doPush(rpcClientId, subscriber, pushData);
        verify(pushService).pushWithoutAck(eq(rpcClientId), any(NotifySubscriberRequest.class));
    }
    
    @Test
    void testDoPushWithCallback() {
        doAnswer(new CallbackAnswer()).when(pushService)
                .pushWithCallback(eq(rpcClientId), any(NotifySubscriberRequest.class), eq(pushCallBack),
                        eq(GlobalExecutor.getCallbackExecutor()));
        pushExecutor.doPushWithCallback(rpcClientId, subscriber, pushData, pushCallBack);
        verify(pushCallBack).onSuccess();
    }
    
    private class CallbackAnswer implements Answer<Void> {
        
        @Override
        public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
            NotifySubscriberRequest pushRequest = invocationOnMock.getArgument(1);
            assertEquals(pushData.getOriginalData().toString(), pushRequest.getServiceInfo().toString());
            PushCallBack callBack = invocationOnMock.getArgument(2);
            callBack.onSuccess();
            return null;
        }
    }
}
