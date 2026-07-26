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

package com.alibaba.nacos.api.lock.remote.request;

import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.AbstractLockRequest;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;

/**
 * grpc acquire lock request.
 *
 * @author 985492783@qq.com
 * @description AcquireLockRequest
 * @date 2023/6/29 12:01
 */
public class LockOperationRequest extends AbstractLockRequest {
    
    private LockInstance lockInstance;
    
    private LockOperationEnum lockOperationEnum;
    
    public LockInstance getLockInstance() {
        return lockInstance;
    }
    
    public void setLockInstance(LockInstance lockInstance) {
        this.lockInstance = lockInstance;
    }
    
    public LockOperationEnum getLockOperationEnum() {
        return lockOperationEnum;
    }
    
    public void setLockOperationEnum(LockOperationEnum lockOperationEnum) {
        this.lockOperationEnum = lockOperationEnum;
    }
}
