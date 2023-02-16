/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

/**
 * {@link RpcPushService} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 19:35
 */
@RunWith(MockitoJUnitRunner.class)
public class RpcPushServiceTest {
    
    @InjectMocks
    private RpcPushService rpcPushService;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private GrpcConnection grpcConnection;
    
    private String connectId = UUID.randomUUID().toString();
    
    @Test
    public void testPushWithCallback() {
        try {
            Mockito.when(connectionManager.getConnection(Mockito.any())).thenReturn(null);
            rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
                @Override
                public long getTimeout() {
                    return 0;
                }
    
                @Override
                public void onSuccess() {
                    System.out.println("success");
                }
    
                @Override
                public void onFail(Throwable e) {
                    e.printStackTrace();
                    Assert.fail(e.getMessage());
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testPushWithoutAck() {
        Mockito.when(connectionManager.getConnection(Mockito.any())).thenReturn(grpcConnection);
        try {
            Mockito.when(grpcConnection.request(Mockito.any(), Mockito.eq(3000L)))
                    .thenThrow(ConnectionAlreadyClosedException.class);
            rpcPushService.pushWithoutAck(connectId, null);
    
            Mockito.when(grpcConnection.request(Mockito.any(), Mockito.eq(3000L)))
                    .thenThrow(NacosException.class);
            rpcPushService.pushWithoutAck(connectId, null);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        
        try {
            Mockito.when(grpcConnection.request(Mockito.any(), Mockito.eq(3000L))).thenReturn(Mockito.any());
            rpcPushService.pushWithoutAck(connectId, null);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
