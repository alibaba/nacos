/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.raft.grpc;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alipay.sofa.jraft.rpc.InvokeCallback;
import com.alipay.sofa.jraft.rpc.impl.MarshallerRegistry;
import com.alipay.sofa.jraft.util.Endpoint;
import com.google.protobuf.Message;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosGrpcClientTest {
    
    @Test
    @SuppressWarnings("unchecked")
    void testInvokeAsyncAttachesCallCredentials() {
        MarshallerRegistry marshallerRegistry = mock(MarshallerRegistry.class);
        when(marshallerRegistry.findResponseInstanceByRequest(ReadRequest.class.getName()))
            .thenReturn(Response.getDefaultInstance());
        CallCredentials credentials = mock(CallCredentials.class);
        NacosGrpcClient client = new NacosGrpcClient(
            Collections.singletonMap(ReadRequest.class.getName(),
                ReadRequest.getDefaultInstance()),
            marshallerRegistry, credentials);
        Endpoint endpoint = new Endpoint("127.0.0.1", 7848);
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.getState(false)).thenReturn(ConnectivityState.READY);
        ClientCall<Message, Message> call = mock(ClientCall.class);
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class))).thenReturn(call);
        Map<Endpoint, ManagedChannel> channelPool =
            (Map<Endpoint, ManagedChannel>) ReflectionTestUtils.getField(client,
                "managedChannelPool");
        channelPool.put(endpoint, channel);
        InvokeCallback callback = mock(InvokeCallback.class);
        ArgumentCaptor<CallOptions> callOptionsCaptor = ArgumentCaptor.forClass(CallOptions.class);
        
        client.invokeAsync(endpoint, ReadRequest.getDefaultInstance(), null, callback, 1000L);
        
        verify(channel).newCall(any(MethodDescriptor.class), callOptionsCaptor.capture());
        assertSame(credentials, callOptionsCaptor.getValue().getCredentials());
    }
}
