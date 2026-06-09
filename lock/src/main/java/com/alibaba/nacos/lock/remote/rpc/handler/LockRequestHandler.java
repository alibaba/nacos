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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.lock.exception.NacosLockException;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * lock grpc handler.
 *
 * @author 985492783@qq.com
 * @description LockRequestHandler
 * @date 2023/6/29 14:00
 */
@Since("3.0.0")
@Component
public class LockRequestHandler
    extends RequestHandler<LockOperationRequest, LockOperationResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LockRequestHandler.class);
    
    private final LockOperationService lockOperationService;
    
    public LockRequestHandler(LockOperationService lockOperationService) {
        this.lockOperationService = lockOperationService;
    }
    
    @Override
    public LockOperationResponse handle(LockOperationRequest request, RequestMeta meta)
        throws NacosException {
        LOGGER.debug("request: {}, instance: {}", request.getLockOperationEnum(),
            request.getLockInstance());
        try {
            LockInstance lockInstance = request.getLockInstance();
            
            // Validate lock instance
            if (lockInstance == null) {
                return LockOperationResponse.fail("LockInstance cannot be null");
            }
            if (lockInstance.getKey() == null || lockInstance.getKey().isEmpty()) {
                return LockOperationResponse.fail("Lock key cannot be null or empty");
            }
            String connectionId = meta.getConnectionId();
            
            if (lockInstance.getOwner() == null || lockInstance.getOwner().isEmpty()) {
                lockInstance.setOwner(connectionId);
            }
            String lockType = lockInstance.getLockType();
            if (!LockConstants.REENTRANT_LOCK_TYPE.equals(lockType)
                && !LockConstants.NON_REENTRANT_LOCK_TYPE.equals(lockType)
                && !LockConstants.NACOS_LOCK_TYPE.equals(lockType)) {
                return LockOperationResponse.fail("Invalid lock type: " + lockType
                    + ", expected " + LockConstants.REENTRANT_LOCK_TYPE
                    + ", " + LockConstants.NON_REENTRANT_LOCK_TYPE
                    + " or " + LockConstants.NACOS_LOCK_TYPE);
            }
            
            if (request.getLockOperationEnum() == LockOperationEnum.ACQUIRE) {
                if (lockInstance.getExpiredTime() == 0) {
                    return LockOperationResponse
                        .fail("Lock expiredTime must be non-zero for ACQUIRE");
                }
                LockResult result = lockOperationService.lock(lockInstance, connectionId);
                return LockOperationResponse.success(result);
            } else if (request.getLockOperationEnum() == LockOperationEnum.RELEASE) {
                LockResult releaseResult = lockOperationService.unLock(lockInstance);
                return LockOperationResponse.success(releaseResult);
            } else if (request.getLockOperationEnum() == LockOperationEnum.RENEW) {
                if (lockInstance.getExpiredTime() == 0) {
                    return LockOperationResponse
                        .fail("Lock expiredTime must be non-zero for RENEW");
                }
                Boolean renewed = lockOperationService.renew(lockInstance);
                return LockOperationResponse.success(renewed);
            } else if (request.getLockOperationEnum() == LockOperationEnum.CANCEL_WAIT) {
                LockResult cancelResult =
                    lockOperationService.cancelWait(lockInstance, connectionId);
                return LockOperationResponse.success(cancelResult);
            } else {
                return LockOperationResponse.fail("There is no Handler of such operations!");
            }
        } catch (NacosLockException e) {
            return LockOperationResponse.fail(e.getMessage());
        }
    }
}
