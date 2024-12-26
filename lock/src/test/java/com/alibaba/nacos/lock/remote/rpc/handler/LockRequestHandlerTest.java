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
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
        
        LockInstance lockInstance = new LockInstance("key", 1L, LockConstants.NACOS_LOCK_TYPE);
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        Mockito.when(lockOperationService.lock(lockInstance)).thenReturn(true);
        LockOperationResponse response = lockRequestHandler.handle(request, null);
        assertTrue((Boolean) response.getResult());
    }
    
    @Test
    public void testReleaseHandler() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance("key", 1L, LockConstants.NACOS_LOCK_TYPE);
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.RELEASE);
        Mockito.when(lockOperationService.unLock(lockInstance)).thenReturn(true);
        LockOperationResponse response = lockRequestHandler.handle(request, null);
        assertTrue((Boolean) response.getResult());
    }
}
