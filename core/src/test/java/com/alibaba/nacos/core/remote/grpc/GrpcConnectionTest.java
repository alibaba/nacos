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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.response.NotifySubscriberResponse;
import com.alibaba.nacos.api.remote.DefaultRequestFuture;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.remote.exception.ConnectionBusyException;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.netty.shaded.io.netty.channel.DefaultEventLoop;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class GrpcConnectionTest {
    
    @Mock
    ServerCallStreamObserver streamObserver;
    
    @Mock
    ControlManagerCenter controlManagerCenter;
    
    @Mock
    TpsControlManager tpsControlManager;
    
    GrpcConnection connection;
    
    MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    @Mock
    private Channel channel;
    
    @AfterEach
    void setDown() throws IOException {
        if (controlManagerCenterMockedStatic != null) {
            controlManagerCenterMockedStatic.close();
        }
    }
    
    @BeforeEach
    void setUp() throws IOException {
        String ip = "1.1.1.1";
        ConnectionMeta connectionMeta = new ConnectionMeta("connectId" + System.currentTimeMillis(), ip, ip, 8888, 9848, "GRPC", "", "",
                new HashMap<>());
        Mockito.when(channel.isOpen()).thenReturn(true);
        Mockito.when(channel.isActive()).thenReturn(true);
        connection = new GrpcConnection(connectionMeta, streamObserver, channel);
        connection.setTraced(true);
        
    }
    
    @Test
    void testStatusRuntimeException() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doThrow(new StatusRuntimeException(Status.CANCELLED)).when(streamObserver).onNext(Mockito.any());
        Mockito.doReturn(true).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof ConnectionAlreadyClosedException);
            assertTrue(e.getCause() instanceof StatusRuntimeException);
            
        }
    }
    
    @Test
    void testIllegalStateException() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doThrow(new IllegalStateException()).when(streamObserver).onNext(Mockito.any());
        Mockito.doReturn(true).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 1000L);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof ConnectionAlreadyClosedException);
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }
    
    @Test
    void testOtherException() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doThrow(new Error("OOM")).when(streamObserver).onNext(Mockito.any());
        Mockito.doReturn(true).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            assertTrue(false);
        } catch (Throwable e) {
            assertTrue(e instanceof NacosRuntimeException);
            assertTrue(e.getCause() instanceof Error);
        }
    }
    
    @Test
    void testNormal() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doReturn(true).when(streamObserver).isReady();
        assertTrue(connection.isConnected());
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    long start = System.currentTimeMillis();
                    while ((System.currentTimeMillis() - start < 3000L)) {
                        Map<String, DefaultRequestFuture> stringDefaultRequestFutureMap = RpcAckCallbackSynchronizer.initContextIfNecessary(
                                connection.getMetaInfo().getConnectionId());
                        if (!stringDefaultRequestFutureMap.entrySet().iterator().hasNext()) {
                            try {
                                Thread.sleep(100L);
                                continue;
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        Map.Entry<String, DefaultRequestFuture> next = stringDefaultRequestFutureMap.entrySet().iterator().next();
                        NotifySubscriberResponse notifySubscriberResponse = new NotifySubscriberResponse();
                        notifySubscriberResponse.setRequestId(next.getValue().getRequestId());
                        try {
                            RpcAckCallbackSynchronizer.ackNotify(connection.getMetaInfo().getConnectionId(), notifySubscriberResponse);
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                }
            }).start();
            connection.request(new NotifySubscriberRequest(), 3000L);
            assertTrue(true);
        } catch (Throwable e) {
            e.printStackTrace();
            assertFalse(true);
        }
    }
    
    @Test
    void testBusy() {
        controlManagerCenterMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        Mockito.when(ControlManagerCenter.getInstance()).thenReturn(controlManagerCenter);
        Mockito.when(ControlManagerCenter.getInstance().getTpsControlManager()).thenReturn(tpsControlManager);
        Mockito.when(tpsControlManager.check(Mockito.any())).thenReturn(new TpsCheckResponse(true, 200, ""));
        Mockito.doReturn(false).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof ConnectionBusyException);
        }
        
        try {
            Thread.sleep(3001);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof ConnectionBusyException);
        }
        
        assertTrue(connection.getMetaInfo().pushQueueBlockTimesLastOver(3000));
        
    }
    
    @Test
    void testClose() {
        
        Mockito.doThrow(new IllegalStateException()).when(streamObserver).onCompleted();
        connection.close();
        Mockito.verify(channel, Mockito.times(1)).close();
        
    }
}
