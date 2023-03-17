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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import io.grpc.ManagedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GrpcClientTest {

    protected GrpcClient grpcClient;

    @Mock(lenient = true)
    RpcClientTlsConfig tlsConfig;

    protected Method createNewManagedChannelMethod;

    protected Method createNewChannelStubMethod;

    protected ManagedChannel managedChannel;

    protected RpcClient.ServerInfo serverInfo;

    @Mock(lenient = true)
    protected GrpcClientConfig clientConfig;

    protected void init() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(clientConfig.channelKeepAlive()).thenReturn(6 * 60 * 1000);
        when(clientConfig.channelKeepAliveTimeout()).thenReturn(TimeUnit.SECONDS.toMillis(20L));
        RpcClient.ServerInfo serverInfo = spy(new RpcClient.ServerInfo("10.10.10.10", 8848));
        createNewManagedChannelMethod = GrpcClient.class.getDeclaredMethod("createNewManagedChannel", String.class,
                int.class);
        createNewManagedChannelMethod.setAccessible(true);
        int port = serverInfo.getServerPort() + grpcClient.rpcPortOffset();
        managedChannel = (ManagedChannel) createNewManagedChannelMethod.invoke(grpcClient, serverInfo.getServerIp(),
                port);
    }

    @Before
    public void setUp() throws Exception {
        when(clientConfig.name()).thenReturn("testClient");
        grpcClient = spy(new GrpcClient(clientConfig) {
            @Override
            public int rpcPortOffset() {
                return 1000;
            }
        });
        when(clientConfig.tlsConfig()).thenReturn(tlsConfig);

        init();
    }

    @Test
    public void testCreateNewManagedChannel() throws InvocationTargetException, IllegalAccessException {
        GrpcConnection grpcConnection = new GrpcConnection(serverInfo, null);
        grpcConnection.setChannel(managedChannel);
    }

    @Test
    public void createNewChannelStub() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        createNewChannelStubMethod = GrpcClient.class.getDeclaredMethod("createNewChannelStub", ManagedChannel.class);
        createNewChannelStubMethod.setAccessible(true);
        Object invoke = createNewChannelStubMethod.invoke(grpcClient, managedChannel);
        Assert.assertTrue(invoke instanceof RequestGrpc.RequestFutureStub);
    }

    @After
    public void close() {
        managedChannel.shutdownNow();
    }

}
