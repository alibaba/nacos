/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.lock.remote.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.remote.AbstractLockRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.lock.core.NLockFactory;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockGrpcClientTest {
    
    @Mock
    private RpcClient rpcClient;
    
    @Mock
    private SecurityProxy securityProxy;
    
    @Mock
    private ServerListFactory serverListFactory;
    
    private LockGrpcClient lockGrpcClient;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        lockGrpcClient = new LockGrpcClient(NacosClientProperties.PROTOTYPE, serverListFactory, securityProxy);
        Field rpcClientField = LockGrpcClient.class.getDeclaredField("rpcClient");
        rpcClientField.setAccessible(true);
        rpcClientField.set(lockGrpcClient, rpcClient);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        lockGrpcClient.shutdown();
    }
    
    private void mockRequest() {
        Map<String, String> context = new HashMap<>();
        when(securityProxy.getIdentityContext(any())).thenReturn(context);
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK)).thenReturn(AbilityStatus.SUPPORTED);
    }
    
    @Test
    void lockNotSupportedFeature() {
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class, () -> lockGrpcClient.lock(NLockFactory.getLock("test", -1L)));
    }
    
    @Test
    void lockWithNacosException() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class))).thenThrow(new NacosException(NacosException.SERVER_ERROR, "test"));
        assertThrows(NacosException.class, () -> lockGrpcClient.lock(NLockFactory.getLock("test", -1L)), "test");
    }
    
    @Test
    void lockWithOtherException() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class))).thenThrow(new RuntimeException("test"));
        assertThrows(NacosException.class, () -> lockGrpcClient.lock(NLockFactory.getLock("test", -1L)), "Request nacos server failed: test");
    }
    
    @Test
    void lockWithUnexpectedResponse() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class))).thenReturn(new ServerCheckResponse());
        assertThrows(NacosException.class, () -> lockGrpcClient.lock(NLockFactory.getLock("test", -1L)), "Server return invalid response");
    }
    
    @Test
    void lockFailed() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class))).thenReturn(ErrorResponse.build(500, "test fail code"));
        assertThrows(NacosException.class, () -> lockGrpcClient.lock(NLockFactory.getLock("test", -1L)), "test fail code");
    }
    
    @Test
    void lockSuccess() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class))).thenReturn(new LockOperationResponse(true));
        assertTrue(lockGrpcClient.lock(NLockFactory.getLock("test", -1L)));
    }
    
    @Test
    void unLockNotSupportedFeature() {
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK)).thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class, () -> lockGrpcClient.unLock(NLockFactory.getLock("test", -1L)));
    }
    
    @Test
    void unlockSuccess() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class))).thenReturn(new LockOperationResponse(true));
        assertTrue(lockGrpcClient.unLock(NLockFactory.getLock("test", -1L)));
    }
}