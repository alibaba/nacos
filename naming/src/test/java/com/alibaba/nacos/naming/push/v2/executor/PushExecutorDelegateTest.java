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
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.push.v2.task.NamingPushCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushExecutorDelegateTest {
    
    private final String udpClientId = "1.1.1.1:60000#true";
    
    private final String rpcClientId = UUID.randomUUID().toString();
    
    @Mock
    private PushExecutorRpcImpl pushExecutorRpc;
    
    @Mock
    private PushExecutorUdpImpl pushExecutorUdp;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private NamingPushCallback pushCallBack;
    
    private PushDataWrapper pushdata;
    
    private PushExecutorDelegate delegate;
    
    private ServiceMetadata serviceMetadata;
    
    @BeforeEach
    void setUp() throws Exception {
        serviceMetadata = new ServiceMetadata();
        pushdata = new PushDataWrapper(serviceMetadata, new ServiceInfo("G@@S"));
        delegate = new PushExecutorDelegate(pushExecutorRpc, pushExecutorUdp);
    }
    
    @Test
    void testDoPushForUdp() {
        delegate.doPush(udpClientId, subscriber, pushdata);
        verify(pushExecutorUdp).doPush(udpClientId, subscriber, pushdata);
    }
    
    @Test
    void testDoPushForRpc() {
        delegate.doPush(rpcClientId, subscriber, pushdata);
        verify(pushExecutorRpc).doPush(rpcClientId, subscriber, pushdata);
    }
    
    @Test
    void doPushWithCallbackForUdp() {
        delegate.doPushWithCallback(udpClientId, subscriber, pushdata, pushCallBack);
        verify(pushExecutorUdp).doPushWithCallback(udpClientId, subscriber, pushdata, pushCallBack);
    }
    
    @Test
    void doPushWithCallbackForRpc() {
        delegate.doPushWithCallback(rpcClientId, subscriber, pushdata, pushCallBack);
        verify(pushExecutorRpc).doPushWithCallback(rpcClientId, subscriber, pushdata, pushCallBack);
    }
}
