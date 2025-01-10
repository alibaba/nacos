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
import com.alibaba.nacos.api.lock.model.LockInstance;
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
@Component
public class LockRequestHandler extends RequestHandler<LockOperationRequest, LockOperationResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LockRequestHandler.class);
    
    private final LockOperationService lockOperationService;
    
    public LockRequestHandler(LockOperationService lockOperationService) {
        this.lockOperationService = lockOperationService;
    }
    
    /**
     * TODO Support auth.
     */
    @Override
    public LockOperationResponse handle(LockOperationRequest request, RequestMeta meta) throws NacosException {
        Boolean lock = null;
        LOGGER.info("request: {}, instance: {}", request.getLockOperationEnum(), request.getLockInstance());
        try {
            if (request.getLockOperationEnum() == LockOperationEnum.ACQUIRE) {
                LockInstance lockInstance = request.getLockInstance();
                lock = lockOperationService.lock(lockInstance);
            } else if (request.getLockOperationEnum() == LockOperationEnum.RELEASE) {
                lock = lockOperationService.unLock(request.getLockInstance());
            } else {
                return LockOperationResponse.fail("There is no Handler of such operations!");
            }
            return LockOperationResponse.success(lock);
        } catch (NacosLockException e) {
            return LockOperationResponse.fail(e.getMessage());
        }
    }
}
