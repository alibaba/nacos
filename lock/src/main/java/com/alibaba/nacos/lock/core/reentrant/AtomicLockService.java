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

package com.alibaba.nacos.lock.core.reentrant;

import com.alibaba.nacos.api.lock.model.LockInstance;

/**
 * Atomic Lock Service.
 *
 * @author 985492783@qq.com
 * @description AtomicLockService
 * @date 2023/7/10 15:34
 */
public interface AtomicLockService {
    
    /**
     * try lock with expireTime.
     *
     * @param instance request Lock
     * @return boolean
     */
    Boolean tryLock(LockInstance instance);
    
    /**
     * release lock.
     *
     * @param instance instance
     * @return boolean
     */
    Boolean unLock(LockInstance instance);
    
    /**
     * return is auto expire.
     * @return Boolean
     */
    Boolean autoExpire();
}
