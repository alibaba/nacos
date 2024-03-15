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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class GrpcConnectionTest {
    
    @Mock
    private Channel channel;
    
    @Mock
    ServerCallStreamObserver streamObserver;
    
    @Mock
    ControlManagerCenter controlManagerCenter;
    
    @Mock
    TpsControlManager tpsControlManager;
    
    GrpcConnection connection;
    
    MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    @After
    public void setDown() throws IOException {
        if (controlManagerCenterMockedStatic != null) {
            controlManagerCenterMockedStatic.close();
        }
    }
    
    @Before
    public void setUp() throws IOException {
        String ip = "1.1.1.1";
        ConnectionMeta connectionMeta = new ConnectionMeta("connectId" + System.currentTimeMillis(), ip, ip, 8888, 9848,
                "GRPC", "", "", new HashMap<>());
        Mockito.when(channel.isOpen()).thenReturn(true);
        Mockito.when(channel.isActive()).thenReturn(true);
        connection = new GrpcConnection(connectionMeta, streamObserver, channel);
        connection.setTraced(true);
        
    }
    
    @Test
    public void testStatusRuntimeException() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doThrow(new StatusRuntimeException(Status.CANCELLED)).when(streamObserver).onNext(Mockito.any());
        Mockito.doReturn(true).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ConnectionAlreadyClosedException);
            Assert.assertTrue(e.getCause() instanceof StatusRuntimeException);
            
        }
    }
    
    @Test
    public void testIllegalStateException() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doThrow(new IllegalStateException()).when(streamObserver).onNext(Mockito.any());
        Mockito.doReturn(true).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 1000L);
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ConnectionAlreadyClosedException);
            Assert.assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }
    
    @Test
    public void testOtherException() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doThrow(new Error("OOM")).when(streamObserver).onNext(Mockito.any());
        Mockito.doReturn(true).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            Assert.assertTrue(false);
        } catch (Throwable e) {
            Assert.assertTrue(e instanceof NacosRuntimeException);
            Assert.assertTrue(e.getCause() instanceof Error);
        }
    }
    
    @Test
    public void testNormal() {
        Mockito.doReturn(new DefaultEventLoop()).when(channel).eventLoop();
        Mockito.doReturn(true).when(streamObserver).isReady();
        Assert.assertTrue(connection.isConnected());
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
                        Map.Entry<String, DefaultRequestFuture> next = stringDefaultRequestFutureMap.entrySet()
                                .iterator().next();
                        NotifySubscriberResponse notifySubscriberResponse = new NotifySubscriberResponse();
                        notifySubscriberResponse.setRequestId(next.getValue().getRequestId());
                        try {
                            RpcAckCallbackSynchronizer.ackNotify(connection.getMetaInfo().getConnectionId(),
                                    notifySubscriberResponse);
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                }
            }).start();
            connection.request(new NotifySubscriberRequest(), 3000L);
            Assert.assertTrue(true);
        } catch (Throwable e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }
    
    @Test
    public void testBusy() {
        controlManagerCenterMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        Mockito.when(ControlManagerCenter.getInstance()).thenReturn(controlManagerCenter);
        Mockito.when(ControlManagerCenter.getInstance().getTpsControlManager()).thenReturn(tpsControlManager);
        Mockito.when(tpsControlManager.check(Mockito.any())).thenReturn(new TpsCheckResponse(true, 200, ""));
        Mockito.doReturn(false).when(streamObserver).isReady();
        
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ConnectionBusyException);
        }
        
        try {
            Thread.sleep(3001);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            connection.request(new NotifySubscriberRequest(), 3000L);
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ConnectionBusyException);
        }
        
        Assert.assertTrue(connection.getMetaInfo().pushQueueBlockTimesLastOver(3000));
        
    }
    
    @Test
    public void testClose() {
        
        Mockito.doThrow(new IllegalStateException()).when(streamObserver).onCompleted();
        connection.close();
        Mockito.verify(channel, Mockito.times(1)).close();
        
    }
}
