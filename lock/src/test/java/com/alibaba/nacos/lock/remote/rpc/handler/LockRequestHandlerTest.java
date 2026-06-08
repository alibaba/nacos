/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.lock.exception.NacosLockException;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lockRequest handler test.
 *
 * @author 985492783@qq.com
 * @date 2023/9/1 10:00
 */
@ExtendWith(MockitoExtension.class)
public class LockRequestHandlerTest {
    
    @Mock
    private LockOperationService lockOperationService;
    
    private LockRequestHandler lockRequestHandler;
    
    @Test
    public void testAcquireHandler() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        Mockito.when(lockOperationService.lock(Mockito.eq(lockInstance), Mockito.anyString()))
            .thenReturn(LockResult.success(1));
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        LockResult result = response.getLockResult();
        assertTrue(result.isSuccess());
    }
    
    @Test
    public void testAcquireWithZeroExpiredTimeShouldFailValidation() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(0L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        // expiredTime 为 0 时应返回校验错误
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(),
            "expiredTime 为 0 时 ACQUIRE 应校验失败");
    }
    
    @Test
    public void testRenewWithZeroExpiredTimeShouldFailValidation() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(0L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.RENEW);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        // expiredTime 为 0 时应返回校验错误
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(),
            "expiredTime 为 0 时 RENEW 应校验失败");
    }
    
    @Test
    public void testReleaseHandler() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.RELEASE);
        Mockito.when(lockOperationService.unLock(lockInstance)).thenReturn(LockResult.success(0));
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        LockResult result = response.getLockResult();
        assertTrue(result.isSuccess());
    }
    
    @Test
    public void testHandleNullLockInstance() throws NacosException {
        // lockInstance 为 null 时应直接返回失败
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(null);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(), "lockInstance 为 null 时应返回失败");
        assertEquals("LockInstance cannot be null", response.getMessage());
    }
    
    @Test
    public void testHandleEmptyKey() throws NacosException {
        // lockInstance 的 key 为空字符串时应返回失败
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(), "key 为空字符串时应返回失败");
        assertEquals("Lock key cannot be null or empty", response.getMessage());
    }
    
    @Test
    public void testHandleNullKey() throws NacosException {
        // lockInstance 的 key 为 null 时应返回失败
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey(null);
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(), "key 为 null 时应返回失败");
        assertEquals("Lock key cannot be null or empty", response.getMessage());
    }
    
    @Test
    public void testHandleInvalidLockType() throws NacosException {
        // lockType 不合法时应返回失败
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType("invalid-lock-type");
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(), "lockType 不合法时应返回失败");
        assertTrue(response.getMessage().contains("Invalid lock type"),
            "错误信息应包含 'Invalid lock type'");
    }
    
    @Test
    public void testRenewHappyPath() throws NacosException {
        // RENEW 操作，expiredTime 有效时应调用 renew 并返回成功
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.RENEW);
        Mockito.when(lockOperationService.renew(lockInstance)).thenReturn(true);
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertTrue(response.isSuccess(), "RENEW 正常路径应返回成功");
        assertNotNull(response.getResult(), "RENEW 结果不应为 null");
        Mockito.verify(lockOperationService).renew(lockInstance);
    }
    
    @Test
    public void testCancelWaitOperation() throws NacosException {
        // CANCEL_WAIT 操作应调用 cancelWait 并返回成功
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.CANCEL_WAIT);
        Mockito.when(lockOperationService.cancelWait(Mockito.eq(lockInstance), Mockito.anyString()))
            .thenReturn(LockResult.success(0));
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertTrue(response.isSuccess(), "CANCEL_WAIT 正常路径应返回成功");
        Mockito.verify(lockOperationService).cancelWait(Mockito.eq(lockInstance),
            Mockito.anyString());
    }
    
    @Test
    public void testOwnerFallbackToConnectionId() throws NacosException {
        // owner 为 null 时应自动回退为 connectionId
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner(null);
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        Mockito.when(lockOperationService.lock(Mockito.eq(lockInstance), Mockito.anyString()))
            .thenReturn(LockResult.success(1));
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        lockRequestHandler.handle(request, meta);
        assertEquals("test-conn-id", lockInstance.getOwner(),
            "owner 为 null 时应回退为 connectionId");
    }
    
    @Test
    public void testOwnerFallbackToConnectionIdWhenEmpty() throws NacosException {
        // owner 为空字符串时应自动回退为 connectionId
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        Mockito.when(lockOperationService.lock(Mockito.eq(lockInstance), Mockito.anyString()))
            .thenReturn(LockResult.success(1));
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        lockRequestHandler.handle(request, meta);
        assertEquals("test-conn-id", lockInstance.getOwner(),
            "owner 为空字符串时应回退为 connectionId");
    }
    
    @Test
    public void testHandleNacosLockException() throws NacosException {
        // lockOperationService 抛出 NacosLockException 时应返回失败并携带错误信息
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("key");
        lockInstance.setOwner("test-owner");
        lockInstance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lockInstance.setExpiredTime(30000L);
        
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        Mockito.when(lockOperationService.lock(Mockito.eq(lockInstance), Mockito.anyString()))
            .thenThrow(new NacosLockException("lock service unavailable"));
        
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("test-conn-id");
        
        LockOperationResponse response = lockRequestHandler.handle(request, meta);
        assertFalse(response.isSuccess(), "NacosLockException 时应返回失败");
        assertEquals("lock service unavailable", response.getMessage(),
            "错误信息应来自 NacosLockException");
    }
}
