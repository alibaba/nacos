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
import com.alibaba.nacos.api.naming.remote.request.AbstractFuzzyWatchNotifyRequest;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.push.v2.task.NamingPushCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushExecutorDelegateTest {
    
    private final String rpcClientId = UUID.randomUUID().toString();
    
    private final Set<SpiPushExecutor> addedPushExecutors = new HashSet<>();
    
    @Mock
    private PushExecutorRpcImpl pushExecutorRpc;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private NamingPushCallback pushCallBack;
    
    @Mock
    private AbstractFuzzyWatchNotifyRequest watchNotifyRequest;
    
    @Mock
    private PushCallBack fuzzyWatchPushCallBack;
    
    @Mock
    private SpiPushExecutor spiPushExecutor;
    
    private PushDataWrapper pushdata;
    
    private PushExecutorDelegate delegate;
    
    private ServiceMetadata serviceMetadata;
    
    @BeforeEach
    void setUp() throws Exception {
        serviceMetadata = new ServiceMetadata();
        pushdata = new PushDataWrapper(serviceMetadata, new ServiceInfo("G@@S"));
        delegate = new PushExecutorDelegate(pushExecutorRpc);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        getPushExecutors().removeAll(addedPushExecutors);
    }
    
    @Test
    void testDoPushForRpc() {
        delegate.doPush(rpcClientId, subscriber, pushdata);
        verify(pushExecutorRpc).doPush(rpcClientId, subscriber, pushdata);
    }
    
    @Test
    void doPushWithCallbackForRpc() {
        delegate.doPushWithCallback(rpcClientId, subscriber, pushdata, pushCallBack);
        verify(pushExecutorRpc).doPushWithCallback(rpcClientId, subscriber, pushdata, pushCallBack);
    }
    
    @Test
    void testDoFuzzyWatchNotifyPushWithCallBackForRpc() {
        delegate.doFuzzyWatchNotifyPushWithCallBack(rpcClientId, watchNotifyRequest,
            fuzzyWatchPushCallBack);
        
        verify(pushExecutorRpc).doFuzzyWatchNotifyPushWithCallBack(rpcClientId, watchNotifyRequest,
            fuzzyWatchPushCallBack);
    }
    
    @Test
    void testDoPushForSpiExecutor() throws Exception {
        when(spiPushExecutor.isInterest(rpcClientId, subscriber)).thenReturn(true);
        registerSpiPushExecutor(spiPushExecutor);
        
        delegate.doPush(rpcClientId, subscriber, pushdata);
        delegate.doPushWithCallback(rpcClientId, subscriber, pushdata, pushCallBack);
        
        verify(spiPushExecutor).doPush(rpcClientId, subscriber, pushdata);
        verify(spiPushExecutor).doPushWithCallback(rpcClientId, subscriber, pushdata, pushCallBack);
        verify(pushExecutorRpc, never()).doPush(rpcClientId, subscriber, pushdata);
        verify(pushExecutorRpc, never()).doPushWithCallback(rpcClientId, subscriber, pushdata,
            pushCallBack);
    }
    
    private void registerSpiPushExecutor(SpiPushExecutor spiPushExecutor) throws Exception {
        getPushExecutors().add(spiPushExecutor);
        addedPushExecutors.add(spiPushExecutor);
    }
    
    @SuppressWarnings("unchecked")
    private Set<SpiPushExecutor> getPushExecutors() throws Exception {
        Field pushExecutors = SpiImplPushExecutorHolder.class.getDeclaredField("pushExecutors");
        pushExecutors.setAccessible(true);
        return (Set<SpiPushExecutor>) pushExecutors.get(SpiImplPushExecutorHolder.getInstance());
    }
}
