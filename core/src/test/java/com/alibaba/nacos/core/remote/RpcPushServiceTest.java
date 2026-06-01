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
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link RpcPushService} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 19:35
 */
@ExtendWith(MockitoExtension.class)
class RpcPushServiceTest {
    
    @InjectMocks
    private RpcPushService rpcPushService;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private GrpcConnection grpcConnection;
    
    private String connectId = UUID.randomUUID().toString();
    
    @Test
    void testPushWithCallbackWhenConnectionNull() {
        try {
            Mockito.when(connectionManager.getConnection(Mockito.any())).thenReturn(null);
            final boolean[] onSuccessCalled = {false};
            rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
                
                @Override
                public long getTimeout() {
                    return 0;
                }
                
                @Override
                public void onSuccess() {
                    onSuccessCalled[0] = true;
                }
                
                @Override
                public void onFail(Throwable e) {
                    fail(e.getMessage());
                }
            }, null);
            assertTrue(onSuccessCalled[0]);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testPushWithCallbackWhenConnectionNonNullResponseSuccess() throws Exception {
        Mockito.when(connectionManager.getConnection(connectId)).thenReturn(grpcConnection);
        doAnswer(invocation -> {
            RequestCallBack cb = invocation.getArgument(1);
            cb.onResponse(new HealthCheckResponse());
            return null;
        }).when(grpcConnection).asyncRequest(any(), any());
        final boolean[] onSuccessCalled = {false};
        rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
            
            @Override
            public long getTimeout() {
                return 1000L;
            }
            
            @Override
            public void onSuccess() {
                onSuccessCalled[0] = true;
            }
            
            @Override
            public void onFail(Throwable e) {
                fail(e.getMessage());
            }
        }, null);
        assertTrue(onSuccessCalled[0]);
    }
    
    @Test
    void testPushWithCallbackWhenResponseFail() throws Exception {
        Mockito.when(connectionManager.getConnection(connectId)).thenReturn(grpcConnection);
        Response failResp = new HealthCheckResponse();
        failResp.setErrorInfo(500, "error");
        doAnswer(invocation -> {
            RequestCallBack cb = invocation.getArgument(1);
            cb.onResponse(failResp);
            return null;
        }).when(grpcConnection).asyncRequest(any(), any());
        final boolean[] onFailCalled = {false};
        rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
            
            @Override
            public long getTimeout() {
                return 1000L;
            }
            
            @Override
            public void onSuccess() {
                fail("expected onFail");
            }
            
            @Override
            public void onFail(Throwable e) {
                onFailCalled[0] = true;
            }
        }, null);
        assertTrue(onFailCalled[0]);
    }
    
    @Test
    void testPushWithCallbackWhenAsyncThrowsConnectionAlreadyClosed() throws Exception {
        Mockito.when(connectionManager.getConnection(connectId)).thenReturn(grpcConnection);
        doThrow(new ConnectionAlreadyClosedException()).when(grpcConnection).asyncRequest(any(),
            any());
        final boolean[] onSuccessCalled = {false};
        rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
            
            @Override
            public long getTimeout() {
                return 1000L;
            }
            
            @Override
            public void onSuccess() {
                onSuccessCalled[0] = true;
            }
            
            @Override
            public void onFail(Throwable e) {
                fail(e.getMessage());
            }
        }, null);
        assertTrue(onSuccessCalled[0]);
        verify(connectionManager).unregister(connectId);
    }
    
    @Test
    void testPushWithCallbackWhenAsyncThrowsException() throws Exception {
        Mockito.when(connectionManager.getConnection(connectId)).thenReturn(grpcConnection);
        doThrow(new RuntimeException("send error")).when(grpcConnection).asyncRequest(any(), any());
        final boolean[] onFailCalled = {false};
        rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
            
            @Override
            public long getTimeout() {
                return 1000L;
            }
            
            @Override
            public void onSuccess() {
                fail("expected onFail");
            }
            
            @Override
            public void onFail(Throwable e) {
                onFailCalled[0] = true;
            }
        }, null);
        assertTrue(onFailCalled[0]);
    }
    
    @Test
    void testPushWithCallbackWhenCallbackOnExceptionInvoked() throws Exception {
        Mockito.when(connectionManager.getConnection(connectId)).thenReturn(grpcConnection);
        Throwable expectedEx = new RuntimeException("async onException");
        doAnswer(invocation -> {
            RequestCallBack cb = invocation.getArgument(1);
            cb.onException(expectedEx);
            return null;
        }).when(grpcConnection).asyncRequest(any(), any());
        final boolean[] onFailCalled = {false};
        final Throwable[] captured = {null};
        rpcPushService.pushWithCallback(connectId, null, new PushCallBack() {
            
            @Override
            public long getTimeout() {
                return 1000L;
            }
            
            @Override
            public void onSuccess() {
                fail("expected onFail");
            }
            
            @Override
            public void onFail(Throwable e) {
                onFailCalled[0] = true;
                captured[0] = e;
            }
        }, null);
        assertTrue(onFailCalled[0]);
        assertTrue(captured[0] == expectedEx);
    }
    
    @Test
    void testPushWithoutAckWhenConnectionNull() {
        Mockito.when(connectionManager.getConnection(connectId)).thenReturn(null);
        rpcPushService.pushWithoutAck(connectId, null);
    }
    
    @Test
    void testPushWithoutAck() {
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
            fail(e.getMessage());
        }
        
        try {
            Mockito.when(grpcConnection.request(Mockito.any(), Mockito.eq(3000L))).thenReturn(null);
            rpcPushService.pushWithoutAck(connectId, null);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
