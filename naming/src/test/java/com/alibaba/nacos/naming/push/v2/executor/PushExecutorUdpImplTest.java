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
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.push.v2.task.NamingPushCallback;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PushExecutorUdpImplTest {
    
    private final String rpcClientId = "1.1.1.1:10000";
    
    @Mock
    private UdpPushService pushService;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private NamingPushCallback pushCallBack;
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    private PushDataWrapper pushData;
    
    private PushExecutorUdpImpl pushExecutor;
    
    private ServiceMetadata serviceMetadata;
    
    @Before
    public void setUp() throws Exception {
        serviceMetadata = new ServiceMetadata();
        pushData = new PushDataWrapper(serviceMetadata, new ServiceInfo("G@@S"));
        pushExecutor = new PushExecutorUdpImpl(pushService);
        doAnswer(new CallbackAnswer()).when(pushService)
                .pushDataWithCallback(eq(subscriber), any(ServiceInfo.class), eq(pushCallBack));
        ApplicationUtils.injectContext(context);
        when(context.getBean(SelectorManager.class)).thenReturn(selectorManager);
        when(selectorManager.select(any(), any(), any()))
                .then((Answer<List<Instance>>) invocationOnMock -> invocationOnMock.getArgument(2));
    }
    
    @Test
    public void testDoPush() {
        pushExecutor.doPush(rpcClientId, subscriber, pushData);
        verify(pushService).pushDataWithoutCallback(eq(subscriber), any(ServiceInfo.class));
    }
    
    @Test
    public void testDoPushWithCallback() {
        pushExecutor.doPushWithCallback(rpcClientId, subscriber, pushData, pushCallBack);
        verify(pushCallBack).onSuccess();
    }
    
    private static class CallbackAnswer implements Answer<Void> {
        
        @Override
        public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
            ServiceInfo serviceInfo = invocationOnMock.getArgument(1);
            assertEquals("G@@S", serviceInfo.getName());
            PushCallBack callBack = invocationOnMock.getArgument(2);
            callBack.onSuccess();
            return null;
        }
    }
}
