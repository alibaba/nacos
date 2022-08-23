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
import io.grpc.ManagedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class GrpcClientTest {
    
    GrpcClient grpcClient;
    
    Method createNewManagedChannelMethod;
    
    Method createNewChannelStubMethod;
    
    ManagedChannel managedChannel;
    
    RpcClient.ServerInfo serverInfo;
    
    @Before
    public void setUp() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Properties clientConfig = new Properties();
        clientConfig.put("nacos.remote.client.grpc.pool.alive", String.valueOf(20));
        clientConfig.put("nacos.remote.client.grpc.timeout", String.valueOf(5000));
        grpcClient = spy(new GrpcClient("testClient", clientConfig) {
            @Override
            public int rpcPortOffset() {
                return 1000;
            }
        });
        RpcClient.ServerInfo serverInfo = spy(new RpcClient.ServerInfo("10.10.10.10", 8848));
        createNewManagedChannelMethod = GrpcClient.class.getDeclaredMethod("createNewManagedChannel", String.class,
                int.class);
        createNewManagedChannelMethod.setAccessible(true);
        int port = serverInfo.getServerPort() + grpcClient.rpcPortOffset();
        managedChannel = (ManagedChannel) createNewManagedChannelMethod.invoke(grpcClient, serverInfo.getServerIp(),
                port);
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
